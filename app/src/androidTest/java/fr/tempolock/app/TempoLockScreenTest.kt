package fr.tempolock.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import fr.tempolock.app.domain.InstalledApp
import fr.tempolock.app.domain.LockPhase
import fr.tempolock.app.domain.LockSession
import fr.tempolock.app.domain.LockStatus
import fr.tempolock.app.ui.TempoLockApp
import fr.tempolock.app.ui.theme.TempoLockTheme
import org.junit.Rule
import org.junit.Test

class TempoLockScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idleScreen_requiresExplicitConfirmationPhrase() {
        composeRule.setContent { TestScreen(idleState()) }

        composeRule.onNodeWithText("Snapchat").assertIsDisplayed()
        composeRule.onNodeWithTag("prepare_lock_button").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("confirm_lock_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("confirmation_input").performTextInput("VERROUILLER")
        composeRule.onNodeWithTag("confirm_lock_button").assertIsEnabled()
    }

    @Test
    fun activeScreen_exposesNoManualUnlockAction() {
        composeRule.setContent { TestScreen(activeState()) }

        composeRule.onNodeWithTag("countdown_ring").assertIsDisplayed()
        composeRule.onNodeWithText("Ce délai est désormais immuable.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Déverrouiller", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Annuler", substring = true).assertDoesNotExist()
    }

    @Test
    fun confirmationDialog_isRestoredAfterStateRecreation() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { TestScreen(idleState()) }
        composeRule.onNodeWithTag("prepare_lock_button").performClick()
        composeRule.onNodeWithText("Confirmation irrévocable").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Confirmation irrévocable").assertIsDisplayed()
    }

    @Test
    fun expandedWindowAndLargeFont_keepPrimaryActionReachable() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 1_000.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f),
            ) {
                TestScreen(idleState())
            }
        }

        composeRule.onNodeWithTag("prepare_lock_button").assertIsDisplayed().assertIsEnabled()
    }

    @Composable
    private fun TestScreen(state: TempoLockUiState) {
        TempoLockTheme(darkTheme = true) {
            TempoLockApp(
                state = state,
                onRefresh = {},
                onSelectApp = {},
                onDurationChange = {},
                onStartLock = {},
                onDismissError = {},
                onRequestExactAlarm = {},
            )
        }
    }

    private fun idleState() = TempoLockUiState(
        status = LockStatus.Idle,
        selectedApp = InstalledApp("com.snapchat.android", "Snapchat"),
        durationMinutes = 120,
        exactAlarmsAllowed = true,
        isWorking = false,
    )

    private fun activeState(): TempoLockUiState {
        val now = System.currentTimeMillis()
        return TempoLockUiState(
            status = LockStatus.Active(
                session = LockSession(
                    targetPackage = "com.snapchat.android",
                    targetLabel = "Snapchat",
                    durationMillis = 7_200_000,
                    startedAtEpochMillis = now,
                    startedAtElapsedMillis = 10_000,
                    bootCountAtStart = 7,
                    deadlineEpochMillis = now + 7_200_000,
                    deadlineElapsedMillis = 7_210_000,
                    phase = LockPhase.ACTIVE,
                ),
                remainingMillis = 5_247_000,
            ),
            isWorking = false,
        )
    }
}
