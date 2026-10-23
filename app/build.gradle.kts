plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.invictus.xmd"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.invictus.xmd"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "1.0.0"

        // Commit count of this build: the Preview update channel compares it
        // against the auto-built manifest's commit_count (see preview.yml).
        buildConfigField("int", "GIT_COUNT", getCommitCount())

        // Commit this build was made from ("" when git isn't available). The
        // Preview updater diffs it against the newest preview's commit to
        // summarise what changed in the update sheet's "What's new".
        buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")

        // Run number of the preview.yml workflow run that built this APK (0 for
        // every other build). The Preview update channel compares it with the
        // newest successful run of that workflow.
        buildConfigField("int", "PREVIEW_RUN", "0")
    }

    // Two flavors instead of one do-everything APK:
    //  - lite: no yt-dlp/ffmpeg at all -- everything this app did before
    //          YouTube support existed (FuckingFast/direct/fitgirl/torrent
    //          only). Ships both arm64-v8a and armeabi-v7a (libtorrent4j
    //          has a build for each -- libtorrent4j-android-arm64 and
    //          libtorrent4j-android-arm -- so there's no reason to leave
    //          32-bit devices out here), split into 2 separate APKs.
    //  - full: adds YouTube (yt-dlp) support. The python+ffmpeg binaries
    //          this needs can't be downloaded at runtime on Android 10+
    //          (see core/YtDlpManager.kt in the full/ source set for why),
    //          so this flavor ships them bundled as a separate, larger APK
    //          instead. youtubedl-android supports armeabi-v7a too, so like
    //          lite, this splits into 2 separate per-ABI APKs below rather
    //          than one universal one -- an arm64 device's download never
    //          carries the armeabi-v7a copy of yt-dlp/ffmpeg/python, or
    //          vice versa.
    //
    // ABI selection lives entirely in the `splits { abi { ... } }` block
    // below, NOT in ndk.abiFilters -- AGP rejects having both set for the
    // same ABIs ("Conflicting configuration ... in ndk abiFilters cannot
    // be present when splits abi filters are set"). Since both flavors
    // want the same two ABIs here, one shared splits.abi block is enough.
    flavorDimensions += "engine"
    productFlavors {
        create("lite") {
            dimension = "engine"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "HAS_YOUTUBE_SUPPORT", "false")
        }
        create("full") {
            dimension = "engine"
            versionNameSuffix = "-full"
            buildConfigField("boolean", "HAS_YOUTUBE_SUPPORT", "true")
        }
    }

    // Splits each flavor's two ABIs into separate APKs -- Xmd-lite-arm64-v8a
    // / Xmd-lite-armeabi-v7a / Xmd-full-arm64-v8a / Xmd-full-armeabi-v7a.
    // isUniversalApk = false: no combined-ABI fallback is built, since every
    // real device is one or the other and a universal APK would just mean
    // everyone downloads both ABIs' worth of native libs for nothing.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // No signingConfig here on purpose: this build type produces an
            // unsigned APK (app-release-unsigned.apk). Signing is done
            // explicitly with apksigner in .github/workflows/android-build.yml
            // (or manually for local release testing), keeping build and
            // sign as separate, visible steps.
        }
        debug {
            applicationIdSuffix = ".debug"
        }
        // Auto-built beta (preview.yml): a release clone with the same
        // applicationId, so it installs over/under stable builds signed with
        // the same key. Unsigned here for the same reason as `release`.
        create("preview") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-beta.r${getPreviewRun()}"
            buildConfigField("int", "PREVIEW_RUN", getPreviewRun())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

androidComponents {
    onVariants { variant ->
        val isLite = variant.flavorName == "lite"
        val isDebug = variant.buildType == "debug"
        val appName = when {
            isLite && isDebug -> "Xmd-Lite-Debug"
            isLite -> "Xmd-Lite"
            isDebug -> "Xmd-Debug"
            else -> "Xmd"
        }
        variant.resValues.put(
            variant.makeResValueKey("string", "app_name"),
            com.android.build.api.variant.ResValue(appName)
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.okhttp)
    // Document-start JS injection (popup guard + YouTube ad pruning in the in-app browser).
    implementation(libs.androidx.webkit)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)

    // Room: persists the download queue to disk so it survives app/process
    // restart (previously QueueRepository was in-memory only -- see
    // core/db/AppDatabase.kt).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // libtorrent4j: real BitTorrent engine (magnet links + .torrent files) --
    // see core/TorrentEngine.kt. The main artifact is pure-Java bindings;
    // both native .so variants are declared here -- the splits.abi block
    // above (not ndk.abiFilters, which can't coexist with it) prunes
    // whichever ABI a given output doesn't need.
    implementation(libs.libtorrent4j)
    implementation(libs.libtorrent4j.android.arm64)
    implementation(libs.libtorrent4j.android.arm)

    // YouTube (and future yt-dlp-supported sites) downloading -- Android
    // wrapper around the actual yt-dlp + ffmpeg binaries, on Maven Central.
    // "full"-flavor only: see the flavor comment above for why this can't
    // be a runtime download and has to be an opt-in separate APK instead.
    "fullImplementation"(libs.youtubedl.library)
    "fullImplementation"(libs.youtubedl.ffmpeg)

    // Jetpack Compose -- BOM pins every androidx.compose.* artifact below to
    // mutually-compatible versions, so only the BOM line needs bumping later.
    // The app UI is Compose-first; AndroidView remains only for WebView.
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    // Material 3 Expressive pre-release (see libs.versions.toml) -- overrides the 1.4.0 the BOM maps to;
    // Gradle also lifts compose ui/foundation to what it needs.
    implementation(libs.androidx.compose.material3)
    implementation(libs.icons.material.symbols)
    implementation(libs.icons.material.symbols.rounded.filled)

    // Activity/Fragment <-> Compose interop (setContent {}, ComposeView) and
    // typed navigation between Compose screens, replacing the Fragment-based
    // nav graph as each screen migrates.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.compose)

    // Lets ViewModels expose StateFlow/collectAsStateWithLifecycle() straight
    // into composables -- QueueRepository, BookmarkRepository etc. already
    // expose Flow, so this is the natural consumption point on the UI side.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Preview/inspection tooling for Android Studio's Compose preview pane
    // (debug builds only -- adds nothing to release APK size).
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary)

    testImplementation(libs.junit)
}

// ---------------- Git helpers ----------------

// Must stay a plain integer: it is emitted as `int GIT_COUNT` into BuildConfig.
fun getCommitCount(): String =
    runCommand("git rev-list --count HEAD")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: "0"

// GITHUB_RUN_NUMBER is set by preview.yml (and any other CI run); locally it is
// missing, so 0 -- a local preview build then always offers the latest preview.
// Must stay a plain integer: it is emitted as `int PREVIEW_RUN` into BuildConfig.
fun getPreviewRun(): String =
    System.getenv("GITHUB_RUN_NUMBER")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: "0"

// Emitted as a quoted String into BuildConfig, so only a plain 40-char hex sha is allowed through.
fun getCommitSha(): String =
    runCommand("git rev-parse HEAD")?.takeIf { it.length == 40 && it.all { c -> c.isDigit() || c in 'a'..'f' } } ?: ""

fun runCommand(command: String): String? =
    try {
        val process = ProcessBuilder(command.split(" "))
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output.ifBlank { null }
    } catch (_: Exception) {
        null
    }
