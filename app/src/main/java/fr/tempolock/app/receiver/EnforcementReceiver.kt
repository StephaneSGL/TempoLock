package fr.tempolock.app.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import fr.tempolock.app.domain.SessionCoordinator
import fr.tempolock.app.platform.AndroidUnlockScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EnforcementReceiver : BroadcastReceiver() {
    @Inject
    lateinit var coordinator: SessionCoordinator

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in ALLOWED_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                coordinator.reconcile()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val ALLOWED_ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            AndroidUnlockScheduler.ACTION_RECONCILE_EXACT,
            AndroidUnlockScheduler.ACTION_RECONCILE_WATCHDOG,
        )
    }
}
