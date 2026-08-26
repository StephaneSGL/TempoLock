package fr.tempolock.app.domain

import kotlin.math.max

enum class LockPhase {
    ARMING,
    ACTIVE,
    RELEASING,
}

data class LockSession(
    val schemaVersion: Int = 1,
    val targetPackage: String,
    val targetLabel: String,
    val durationMillis: Long,
    val startedAtEpochMillis: Long,
    val startedAtElapsedMillis: Long,
    val bootCountAtStart: Int,
    val deadlineEpochMillis: Long,
    val deadlineElapsedMillis: Long,
    val phase: LockPhase,
)

data class TimeSnapshot(
    val trustedEpochMillis: Long?,
    val elapsedMillis: Long,
    val bootCount: Int,
)

data class InstalledApp(
    val packageName: String,
    val label: String,
)

sealed interface LockStatus {
    data object OwnerRequired : LockStatus
    data object Idle : LockStatus
    data class Active(
        val session: LockSession,
        val remainingMillis: Long,
    ) : LockStatus

    data class Fault(val message: String) : LockStatus
}

fun LockSession.remainingMillis(now: TimeSnapshot): Long =
    if (now.bootCount == bootCountAtStart && now.elapsedMillis >= startedAtElapsedMillis) {
        max(0L, deadlineElapsedMillis - now.elapsedMillis)
    } else {
        val trustedNow = now.trustedEpochMillis ?: throw TrustedTimeUnavailableException(
            "L'heure réseau Android n'est pas encore disponible après le redémarrage.",
        )
        max(0L, deadlineEpochMillis - trustedNow)
    }

fun LockSession.isExpired(now: TimeSnapshot): Boolean = remainingMillis(now) == 0L

const val MIN_LOCK_DURATION_MILLIS = 60_000L
const val MAX_LOCK_DURATION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
