package com.invictus.xmd

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Toast
import com.invictus.xmd.domain.browser.AdblockFilter
import com.invictus.xmd.domain.update.UpdateChecker
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.BookmarkRepository
import com.invictus.xmd.repository.HistoryRepository
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.repository.ShortcutRepository
import com.invictus.xmd.utils.FaviconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FfApp : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "ff_downloads"
    }

    /** Lives for the whole process, used for fire-and-forget work at
     *  startup (currently just the update check) that shouldn't be tied
     *  to any one Activity/Composable's lifecycle. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        Settings.init(this)
        AdblockFilter.init(this)
        // Loads the previously-persisted queue from disk so it survives
        // process restart (see QueueRepository's persistence docs).
        QueueRepository.init(this)
        ShortcutRepository.init(this)
        BookmarkRepository.init(this)
        HistoryRepository.init(this)
        FaviconLoader.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        checkForUpdateOnLaunch()
    }

    /** Runs once per process start, only if the user opted into
     *  "Auto-check for updates" in About. Silent when there's nothing new
     *  (no "up to date" noise on every cold start) -- only toasts when an
     *  update is actually found, from whichever screen happens to be in
     *  front at the time. Never auto-opens the browser; the user still
     *  taps through from About (or the toast itself, on Android versions
     *  where toasts support that) to go grab it. */
    private fun checkForUpdateOnLaunch() {
        if (!Settings.autoCheckForUpdatesEnabled()) return
        appScope.launch {
            val release = try {
                withContext(Dispatchers.IO) {
                    UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME, Settings.updateChannel())
                }
            } catch (_: UpdateChecker.CheckFailedException) {
                null
            } ?: return@launch

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@FfApp,
                    getString(R.string.about_update_available, release.tagName),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity.javaClass.name.contains("leakcanary", ignoreCase = true)) {
            val rootView = activity.findViewById<View>(android.R.id.content)
            rootView?.let { view ->
                ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                    val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                    val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    v.setPadding(
                        v.paddingLeft,
                        statusBarInsets.top,
                        v.paddingRight,
                        navBarInsets.bottom,
                    )
                    insets
                }
            }
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
