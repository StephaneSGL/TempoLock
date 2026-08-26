package fr.tempolock.app

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.tempolock.app.ui.TempoLockApp
import fr.tempolock.app.ui.theme.TempoLockTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TempoLockTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                TempoLockApp(
                    state = state,
                    onRefresh = { viewModel.refresh() },
                    onSelectApp = viewModel::selectApp,
                    onDurationChange = viewModel::setDurationMinutes,
                    onStartLock = viewModel::startLock,
                    onDismissError = viewModel::dismissError,
                    onRequestExactAlarm = ::requestExactAlarm,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh(reconcile = true)
    }

    private fun requestExactAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) return
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName"),
            ),
        )
    }
}
