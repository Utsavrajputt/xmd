package com.invictus.xmd.domain.account

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.invictus.xmd.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Shown to the user as-is. */
class AccountException(message: String) : Exception(message)

/**
 * "Sign in with GitHub" via the OAuth **device flow**: the app shows a short code, the user
 * enters it at github.com/login/device, and the app polls until GitHub hands back a token.
 * No client secret or redirect URI is needed -- only the public [BuildConfig.GITHUB_CLIENT_ID]
 * (the OAuth App must have "Enable Device Flow" ticked). Scope is just `gist`.
 */
object GitHubAuth {
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSec: Int,
        val expiresInSec: Int,
    )

    data class User(val login: String, val name: String?, val avatarUrl: String?)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean = BuildConfig.GITHUB_CLIENT_ID.isNotBlank()

    suspend fun requestDeviceCode(): DeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.GITHUB_CLIENT_ID)
            .add("scope", "gist")
            .build()
        val json = postJson("https://github.com/login/device/code", body)
        json.optString("error").takeIf { it.isNotEmpty() }?.let {
            throw AccountException(json.optString("error_description").ifEmpty { it })
        }
        DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.optString("verification_uri", "https://github.com/login/device"),
            intervalSec = json.optInt("interval", 5),
            expiresInSec = json.optInt("expires_in", 900),
        )
    }

    /** Polls until the user approves; throws [AccountException] if denied/expired. Cancel the coroutine to stop. */
    suspend fun pollForToken(code: DeviceCode): String {
        var interval = code.intervalSec.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
        while (true) {
            delay(interval * 1000L)
            if (System.currentTimeMillis() > deadline) throw AccountException("The code expired. Try again.")
            val json = withContext(Dispatchers.IO) {
                postJson(
                    "https://github.com/login/oauth/access_token",
                    FormBody.Builder()
                        .add("client_id", BuildConfig.GITHUB_CLIENT_ID)
                        .add("device_code", code.deviceCode)
                        .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                        .build(),
                )
            }
            when (val error = json.optString("error")) {
                "" -> return json.getString("access_token")
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> throw AccountException("The code expired. Try again.")
                "access_denied" -> throw AccountException("Sign-in was cancelled on GitHub.")
                else -> throw AccountException(json.optString("error_description").ifEmpty { error })
            }
        }
    }

    suspend fun fetchUser(token: String): User = withContext(Dispatchers.IO) {
        val (code, text) = call(apiRequest("https://api.github.com/user", token).get().build())
        if (code == 401) throw AccountException("GitHub rejected the sign-in. Please sign in again.")
        if (code !in 200..299) throw AccountException("GitHub error ($code)")
        val json = JSONObject(text)
        User(
            login = json.getString("login"),
            name = json.optString("name").takeIf { it.isNotBlank() && it != "null" },
            avatarUrl = json.optString("avatar_url").takeIf { it.isNotBlank() },
        )
    }

    // ── HTTP helpers (shared with GistBackup) ───────────────────────────

    fun apiRequest(url: String, token: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "XMD")

    /** (status code, body). Network failures become a friendly [AccountException]. */
    fun call(request: Request): Pair<Int, String> = try {
        http.newCall(request).execute().use { it.code to (it.body?.string().orEmpty()) }
    } catch (e: java.io.IOException) {
        throw AccountException("No connection to GitHub.")
    }

    private fun postJson(url: String, body: FormBody): JSONObject {
        val request = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "XMD").post(body).build()
        val (code, text) = call(request)
        if (text.isBlank()) throw AccountException("GitHub error ($code)")
        return runCatching { JSONObject(text) }.getOrElse { throw AccountException("GitHub error ($code)") }
    }
}

/** Keeps the GitHub token encrypted with an Android Keystore AES-GCM key (not in plain prefs). */
internal object TokenCipher {
    private const val ALIAS = "xmd_account_token"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12)) }
        String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }.getOrNull()
}
