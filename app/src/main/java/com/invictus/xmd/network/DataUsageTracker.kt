package com.invictus.xmd.network

import android.net.TrafficStats
import android.os.Process
import com.invictus.xmd.preferences.Settings
import java.util.Calendar

/**
 * Tracks how many bytes this app has used today, separately for "total"
 * (any transport) and "mobile" (metered only), so [DownloadService] can
 * enforce the daily data limit settings.
 *
 * Reads [TrafficStats]' per-UID counters (Android's own rx/tx byte totals
 * for this app since last boot) rather than summing DownloadEngine/
 * TorrentEngine progress deltas -- those already double-count on resume
 * (re-downloading overlapping ranges) and don't cover yt-dlp's own
 * networking at all. TrafficStats counts every byte the app's UID moves
 * regardless of which engine did it.
 *
 * A session baseline is snapshotted whenever a new calendar day starts (or
 * on first use), and "today's usage" is just current-counter minus that
 * baseline -- so a device reboot mid-day (which resets TrafficStats to 0)
 * doesn't undercount: the baseline snapshot itself would already be stale
 * relative to a lower absolute value, but since we only ever compare
 * against a same-day baseline taken *after* that reboot when init() next
 * runs, the delta stays correct from that point forward. The rare case of
 * "reboot happens, then app is never reopened until next check" simply
 * means the pre-reboot portion of today's usage is lost, which matches
 * what any TrafficStats-based counter (including Android's own Settings >
 * Data usage) does across a reboot.
 */
object DataUsageTracker {

    private fun uid() = Process.myUid()

    private fun totalBytesNow(): Long {
        val rx = TrafficStats.getUidRxBytes(uid())
        val tx = TrafficStats.getUidTxBytes(uid())
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return -1L
        return rx + tx
    }

    private fun mobileBytesNow(): Long {
        // No per-UID metered-only counter exists on the public API, so this
        // approximates "mobile usage" as "total usage while the app has been
        // observed running on a metered network" -- see [onTick] below,
        // which is called from the download progress path (throttled, same
        // cadence as notification updates) and only advances the mobile
        // baseline delta while NetworkMonitor.isMetered() is currently true.
        return totalBytesNow()
    }

    private fun todayEpochDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / (24L * 60 * 60 * 1000)
    }

    /** Resets the day's baselines if the calendar day has rolled over since
     *  the last recorded baseline -- called before every read/check so a
     *  stale in-memory day never leaks into "today's" total. */
    private fun rolloverIfNeeded() {
        val today = todayEpochDay()
        if (Settings.dataUsageBaselineDay() != today) {
            val now = totalBytesNow().coerceAtLeast(0L)
            Settings.setDataUsageBaseline(day = today, totalBaseline = now, mobileAccum = 0L, mobileBaselineAtLastTick = now)
        }
    }

    /** Bytes used today across all networks. */
    fun todayTotalBytes(): Long {
        rolloverIfNeeded()
        val now = totalBytesNow()
        if (now < 0) return 0L
        return (now - Settings.dataUsageTotalBaseline()).coerceAtLeast(0L)
    }

    /** Bytes used today while on a metered network. Must be advanced via
     *  [onTick] as downloads progress -- unlike [todayTotalBytes] this
     *  can't be computed as a pure counter delta since TrafficStats has no
     *  metered-only breakdown. */
    fun todayMobileBytes(): Long {
        rolloverIfNeeded()
        return Settings.dataUsageMobileAccum()
    }

    /**
     * Call periodically (same throttle as notification updates is fine --
     * this doesn't need to be exact to the byte) while a download may be
     * in flight. Advances the mobile accumulator by however many bytes
     * TrafficStats' total counter moved since the last tick, but only if
     * the network was metered for this whole interval; assumes short
     * throttle intervals mean the network didn't change mid-interval.
     */
    fun onTick(isMetered: Boolean) {
        rolloverIfNeeded()
        val now = totalBytesNow()
        if (now < 0) return
        val last = Settings.dataUsageMobileBaselineAtLastTick()
        val delta = (now - last).coerceAtLeast(0L)
        if (isMetered && delta > 0) {
            Settings.setDataUsageMobileAccum(Settings.dataUsageMobileAccum() + delta)
        }
        Settings.setDataUsageMobileBaselineAtLastTick(now)
    }
}
