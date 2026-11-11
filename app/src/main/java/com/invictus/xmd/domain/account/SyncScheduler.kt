package com.invictus.xmd.domain.account

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.invictus.xmd.preferences.Settings
import java.util.concurrent.TimeUnit

/**
 * Schedules the background GitHub work: a daily auto-backup, a ~60 s debounced backup after a
 * settings change, and the 15-minute "Send to phone" inbox poll. Everything is cancelled when
 * nobody is signed in or the matching switch is off.
 */
object SyncScheduler {
    private const val DAILY_BACKUP = "xmd_auto_backup_daily"
    private const val DEBOUNCED_BACKUP = "xmd_auto_backup_debounced"
    private const val INBOX_PERIODIC = "xmd_inbox_periodic"
    private const val INBOX_NOW = "xmd_inbox_now"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Call once from Application.onCreate after the managers are initialised. */
    fun init(context: Context) {
        val app = context.applicationContext
        Settings.setBackupChangeListener { onSettingsChanged(app) }
        refresh(app)
        if (AccountManager.account.value != null && AccountManager.inboxEnabled()) pollNow(app)
    }

    /** Re-applies the schedule to the current account and switches. */
    fun refresh(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        val signedIn = AccountManager.account.value != null
        if (signedIn && AccountManager.autoBackupEnabled()) {
            wm.enqueueUniquePeriodicWork(
                DAILY_BACKUP,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS).setConstraints(online).build(),
            )
        } else {
            wm.cancelUniqueWork(DAILY_BACKUP)
            wm.cancelUniqueWork(DEBOUNCED_BACKUP)
        }
        if (signedIn && AccountManager.inboxEnabled()) {
            wm.enqueueUniquePeriodicWork(
                INBOX_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<InboxPollWorker>(15, TimeUnit.MINUTES).setConstraints(online).build(),
            )
        } else {
            wm.cancelUniqueWork(INBOX_PERIODIC)
            wm.cancelUniqueWork(INBOX_NOW)
        }
    }

    private fun onSettingsChanged(context: Context) {
        if (AccountManager.account.value == null || !AccountManager.autoBackupEnabled()) return
        // REPLACE restarts the 60 s timer on every change, so a burst of edits makes one backup.
        WorkManager.getInstance(context).enqueueUniqueWork(
            DEBOUNCED_BACKUP,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().setInitialDelay(60, TimeUnit.SECONDS).setConstraints(online).build(),
        )
    }

    /** Checks the inbox right away (app open). */
    fun pollNow(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            INBOX_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<InboxPollWorker>().setConstraints(online).build(),
        )
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (AccountManager.account.value == null || !AccountManager.autoBackupEnabled()) return Result.success()
        val scope = AccountManager.backupScope()
        if (!scope.any) return Result.success()
        return try {
            AccountManager.backupNow(scope, auto = true)
            Result.success()
        } catch (e: AccountException) {
            // Network blips retry; a rejected sign-in or rate limit will not fix itself soon.
            if (e.message?.startsWith("No connection") == true) Result.retry() else Result.success()
        }
    }
}

class InboxPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (AccountManager.account.value == null || !AccountManager.inboxEnabled()) return Result.success()
        return try {
            GistSync.registerDevice()
            GistSync.poll()
            Result.success()
        } catch (e: AccountException) {
            if (e.message?.startsWith("No connection") == true) Result.retry() else Result.success()
        }
    }
}
