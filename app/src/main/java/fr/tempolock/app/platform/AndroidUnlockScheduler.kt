package fr.tempolock.app.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.tempolock.app.domain.LockSession
import fr.tempolock.app.domain.TrustedClock
import fr.tempolock.app.domain.UnlockScheduler
import fr.tempolock.app.domain.remainingMillis
import fr.tempolock.app.receiver.EnforcementReceiver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidUnlockScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: TrustedClock,
) : UnlockScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(session: LockSession) {
        val now = clock.now()
        val remainingMillis = session.remainingMillis(now)
        // AlarmManager RTC uses the user-visible wall clock, which is a different
        // signal from Android's network clock. Re-anchor every alarm to the current
        // boot's monotonic clock instead of mixing those two time domains.
        val triggerAt = Math.addExact(now.elapsedMillis, remainingMillis)
        schedulePair(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt)
    }

    override fun cancel() {
        alarmManager.cancel(exactPendingIntent())
        alarmManager.cancel(watchdogPendingIntent())
    }

    override fun scheduleRetry(delayMillis: Long) {
        val triggerAt = SystemClock.elapsedRealtime() + delayMillis.coerceAtLeast(5_000L)
        schedulePair(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt)
    }

    override fun exactAlarmsAllowed(): Boolean = alarmManager.canScheduleExactAlarms()

    private fun schedulePair(alarmType: Int, triggerAt: Long) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(alarmType, triggerAt, exactPendingIntent())
        }
        // This independent inexact alarm survives revocation of the exact-alarm
        // special access. Android may deliver it later, but never before triggerAt.
        alarmManager.setAndAllowWhileIdle(alarmType, triggerAt, watchdogPendingIntent())
    }

    private fun exactPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_EXACT,
        Intent(context, EnforcementReceiver::class.java).setAction(ACTION_RECONCILE_EXACT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun watchdogPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE_WATCHDOG,
        Intent(context, EnforcementReceiver::class.java).setAction(ACTION_RECONCILE_WATCHDOG),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_RECONCILE_EXACT = "fr.tempolock.app.ACTION_RECONCILE_EXACT"
        const val ACTION_RECONCILE_WATCHDOG = "fr.tempolock.app.ACTION_RECONCILE_WATCHDOG"
        private const val REQUEST_CODE_EXACT = 7103
        private const val REQUEST_CODE_WATCHDOG = 7104
    }
}
