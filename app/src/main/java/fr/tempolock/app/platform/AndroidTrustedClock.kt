package fr.tempolock.app.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.tempolock.app.domain.TimeSnapshot
import fr.tempolock.app.domain.TrustedClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTrustedClock @Inject constructor(
    @ApplicationContext private val context: Context,
) : TrustedClock {
    override fun now(): TimeSnapshot = TimeSnapshot(
        // This signal is maintained by Android's network time service and cannot be
        // changed through the user-facing date/time controls.
        trustedEpochMillis = runCatching {
            SystemClock.currentNetworkTimeClock().millis()
        }.getOrNull(),
        elapsedMillis = SystemClock.elapsedRealtime(),
        bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrDefault(UNKNOWN_BOOT_COUNT),
    )

    private companion object {
        const val UNKNOWN_BOOT_COUNT = -1
    }
}
