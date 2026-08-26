package fr.tempolock.app

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.tempolock.app.domain.InstalledApp
import fr.tempolock.app.domain.LockSession
import fr.tempolock.app.domain.LockStatus
import fr.tempolock.app.domain.MIN_LOCK_DURATION_MILLIS
import fr.tempolock.app.domain.SessionCoordinator
import fr.tempolock.app.platform.InstalledAppsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TempoLockUiState(
    val status: LockStatus = LockStatus.OwnerRequired,
    val installedApps: List<InstalledApp> = emptyList(),
    val selectedApp: InstalledApp? = null,
    val durationMinutes: Long = 60,
    val exactAlarmsAllowed: Boolean = true,
    val isWorking: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val coordinator: SessionCoordinator,
    private val appsRepository: InstalledAppsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TempoLockUiState(
            selectedApp = savedStateHandle.get<String>(KEY_SELECTED_PACKAGE)?.let { packageName ->
                InstalledApp(
                    packageName = packageName,
                    label = savedStateHandle[KEY_SELECTED_LABEL] ?: packageName,
                )
            },
            durationMinutes = savedStateHandle[KEY_DURATION_MINUTES] ?: 60L,
        ),
    )
    val uiState: StateFlow<TempoLockUiState> = _uiState.asStateFlow()

    private var releaseReconciliationStarted = false
    private var lastTickElapsedMillis = SystemClock.elapsedRealtime()

    init {
        viewModelScope.launch {
            refreshInternal(reconcile = true)
            while (true) {
                delay(1_000)
                tick()
            }
        }
    }

    fun refresh(reconcile: Boolean = true) {
        viewModelScope.launch {
            refreshInternal(reconcile)
        }
    }

    fun selectApp(app: InstalledApp) {
        if (_uiState.value.status !is LockStatus.Idle) return
        savedStateHandle[KEY_SELECTED_PACKAGE] = app.packageName
        savedStateHandle[KEY_SELECTED_LABEL] = app.label
        _uiState.update { it.copy(selectedApp = app, errorMessage = null) }
    }

    fun setDurationMinutes(minutes: Long) {
        if (_uiState.value.status !is LockStatus.Idle) return
        val safeMinutes = minutes.coerceIn(1L, 30L * 24L * 60L)
        savedStateHandle[KEY_DURATION_MINUTES] = safeMinutes
        _uiState.update { it.copy(durationMinutes = safeMinutes, errorMessage = null) }
    }

    fun startLock() {
        val current = _uiState.value
        val selected = current.selectedApp ?: run {
            _uiState.update { it.copy(errorMessage = "Choisis d'abord une application.") }
            return
        }
        if (!current.exactAlarmsAllowed) {
            _uiState.update {
                it.copy(errorMessage = "Autorise d'abord les alarmes exactes pour garantir la fin automatique.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null) }
            try {
                val status = coordinator.arm(
                    targetPackage = selected.packageName,
                    targetLabel = selected.label,
                    durationMillis = (current.durationMinutes * MIN_LOCK_DURATION_MILLIS),
                )
                _uiState.update {
                    it.copy(status = status, installedApps = emptyList(), isWorking = false)
                }
                lastTickElapsedMillis = SystemClock.elapsedRealtime()
            } catch (failure: Throwable) {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        errorMessage = failure.message ?: "Le verrouillage a échoué.",
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private suspend fun refreshInternal(reconcile: Boolean) {
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        val status = try {
            if (reconcile) coordinator.reconcile() else coordinator.currentStatus()
        } catch (failure: Throwable) {
            LockStatus.Fault(failure.message ?: "Erreur Android inconnue.")
        }
        val apps = if (status is LockStatus.Idle) {
            runCatching { appsRepository.launchableUserApps() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        lastTickElapsedMillis = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                status = status,
                installedApps = apps,
                exactAlarmsAllowed = coordinator.exactAlarmsAllowed(),
                isWorking = false,
            )
        }
    }

    private fun tick() {
        val tickAt = SystemClock.elapsedRealtime()
        val elapsed = (tickAt - lastTickElapsedMillis).coerceAtLeast(0L)
        lastTickElapsedMillis = tickAt
        val active = _uiState.value.status as? LockStatus.Active ?: return
        val updated = active.copy(remainingMillis = (active.remainingMillis - elapsed).coerceAtLeast(0L))
        _uiState.update { it.copy(status = updated) }

        if (updated.remainingMillis == 0L && !releaseReconciliationStarted) {
            releaseReconciliationStarted = true
            viewModelScope.launch {
                try {
                    refreshInternal(reconcile = true)
                } finally {
                    releaseReconciliationStarted = false
                }
            }
        }
    }

    private companion object {
        const val KEY_SELECTED_PACKAGE = "selected_package"
        const val KEY_SELECTED_LABEL = "selected_label"
        const val KEY_DURATION_MINUTES = "duration_minutes"
    }
}
