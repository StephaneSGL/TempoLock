package fr.tempolock.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import fr.tempolock.app.TempoLockUiState
import fr.tempolock.app.domain.InstalledApp
import fr.tempolock.app.domain.LockPhase
import fr.tempolock.app.domain.LockSession
import fr.tempolock.app.domain.LockStatus
import fr.tempolock.app.ui.theme.TempoLockTheme
import java.time.ZoneId

private val screenshotState = TempoLockUiState(
    status = LockStatus.Idle,
    selectedApp = InstalledApp("com.snapchat.android", "Snapchat"),
    durationMinutes = 120,
    exactAlarmsAllowed = true,
    isWorking = false,
)

@PreviewTest
@Preview(name = "400x400", widthDp = 400, heightDp = 400)
@Preview(name = "400x500", widthDp = 400, heightDp = 500)
@Preview(name = "400x1000", widthDp = 400, heightDp = 1000)
@Preview(name = "610x400", widthDp = 610, heightDp = 400)
@Preview(name = "610x500", widthDp = 610, heightDp = 500)
@Preview(name = "610x1000", widthDp = 610, heightDp = 1000)
@Preview(name = "900x400", widthDp = 900, heightDp = 400)
@Preview(name = "900x500", widthDp = 900, heightDp = 500)
@Preview(name = "900x1000", widthDp = 900, heightDp = 1000)
@Composable
fun TempoLockResponsiveMatrix() {
    TempoLockTheme(darkTheme = true) { ScreenshotContent() }
}

@PreviewTest
@Preview(
    name = "Mobile clair",
    widthDp = 400,
    heightDp = 500,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
fun TempoLockLightTheme() {
    TempoLockTheme(darkTheme = false) { ScreenshotContent() }
}

@PreviewTest
@Preview(name = "Mobile police 150 %", widthDp = 400, heightDp = 500, fontScale = 1.5f)
@Composable
fun TempoLockLargeFont() {
    TempoLockTheme(darkTheme = true) { ScreenshotContent() }
}

@PreviewTest
@Preview(name = "Verrou actif mobile", widthDp = 400, heightDp = 1000)
@Composable
fun TempoLockActiveState() {
    TempoLockTheme(darkTheme = true) {
        CompositionLocalProvider(LocalDeadlineZoneId provides ZoneId.of("Europe/Paris")) {
            TempoLockApp(
                state = TempoLockUiState(
                    status = LockStatus.Active(
                        session = LockSession(
                            targetPackage = "com.snapchat.android",
                            targetLabel = "Snapchat",
                            durationMillis = 7_200_000,
                            startedAtEpochMillis = 1_788_000_000_000,
                            startedAtElapsedMillis = 10_000,
                            bootCountAtStart = 4,
                            deadlineEpochMillis = 1_788_007_200_000,
                            deadlineElapsedMillis = 7_210_000,
                            phase = LockPhase.ACTIVE,
                        ),
                        remainingMillis = 5_247_000,
                    ),
                    isWorking = false,
                ),
                onRefresh = {},
                onSelectApp = {},
                onDurationChange = {},
                onStartLock = {},
                onDismissError = {},
                onRequestExactAlarm = {},
            )
        }
    }
}

@Composable
private fun ScreenshotContent() {
    TempoLockApp(
        state = screenshotState,
        onRefresh = {},
        onSelectApp = {},
        onDurationChange = {},
        onStartLock = {},
        onDismissError = {},
        onRequestExactAlarm = {},
    )
}
