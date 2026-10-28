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
import com.invictus.xmd.domain.browser.AdblockFilter
import com.invictus.xmd.preferences.Settings
import com.invictus.xmd.repository.BookmarkRepository
import com.invictus.xmd.repository.HistoryRepository
import com.invictus.xmd.repository.QueueRepository
import com.invictus.xmd.repository.ShortcutRepository
import com.invictus.xmd.utils.FaviconLoader
import com.invictus.xmd.utils.GithubAvatarLoader

class FfApp : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "ff_downloads"
        const val DOWNLOAD_COMPLETE_CHANNEL_ID = "ff_downloads_complete"
    }

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
        GithubAvatarLoader.init(this)
        com.invictus.xmd.domain.browser.FmhySync.init(this)
        com.invictus.xmd.domain.browser.FmhySync.autoCheckIfDue()
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
            // Separate, louder channel: the progress channel above is LOW
            // (silent), which is right for a live bar but would make "done" easy to miss.
            manager.createNotificationChannel(
                NotificationChannel(
                    DOWNLOAD_COMPLETE_CHANNEL_ID,
                    getString(R.string.download_complete_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.download_complete_channel_description)
                    setShowBadge(true)
                }
            )
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
