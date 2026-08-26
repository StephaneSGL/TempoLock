package fr.tempolock.app.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionCoordinator @Inject constructor(
    private val store: SessionStore,
    private val clock: TrustedClock,
    private val policy: LockPolicy,
    private val scheduler: UnlockScheduler,
) {
    private val mutex = Mutex()

    suspend fun arm(
        targetPackage: String,
        targetLabel: String,
        durationMillis: Long,
    ): LockStatus = mutex.withLock {
        require(targetPackage.isNotBlank()) { "Aucune application n'est sélectionnée." }
        require(targetLabel.isNotBlank()) { "Le nom de l'application est manquant." }
        require(targetLabel.length <= 200) { "Le nom de l'application est trop long." }
        require(durationMillis in MIN_LOCK_DURATION_MILLIS..MAX_LOCK_DURATION_MILLIS) {
            "La durée doit être comprise entre 1 minute et 30 jours."
        }
        check(policy.isDeviceOwner()) { "TempoLock n'est pas propriétaire de l'appareil." }
        check(store.read() == null) { "Un verrouillage est déjà actif." }
        check(scheduler.exactAlarmsAllowed()) {
            "L'autorisation des alarmes exactes est requise avant le verrouillage."
        }

        // L'heure automatique est imposée avant de calculer l'échéance murale utilisée après reboot.
        policy.prepareTrustedTime()
        val now = clock.now()
        val trustedNow = now.trustedEpochMillis ?: throw TrustedTimeUnavailableException(
            "L'heure réseau Android n'est pas encore disponible. Connecte brièvement l'appareil " +
                "à Internet, puis réessaie : aucun verrou n'a été lancé.",
        )
        check(now.bootCount >= 0) {
            "Android ne fournit pas de compteur de redémarrage fiable sur cet appareil ; " +
                "le verrouillage fort est refusé."
        }
        val session = LockSession(
            targetPackage = targetPackage,
            targetLabel = targetLabel.trim(),
            durationMillis = durationMillis,
            startedAtEpochMillis = trustedNow,
            startedAtElapsedMillis = now.elapsedMillis,
            bootCountAtStart = now.bootCount,
            deadlineEpochMillis = Math.addExact(trustedNow, durationMillis),
            deadlineElapsedMillis = Math.addExact(now.elapsedMillis, durationMillis),
            phase = LockPhase.ARMING,
        )

        // L'état ARMING est écrit avant la politique : après un crash, la réconciliation
        // réappliquera le verrou au lieu de considérer à tort qu'il n'existe pas.
        store.write(session)
        try {
            policy.engage(targetPackage)
            check(policy.isSuspended(targetPackage)) {
                "Android n'a pas confirmé la suspension de l'application."
            }
            val active = session.copy(phase = LockPhase.ACTIVE)
            store.write(active)
            scheduler.schedule(active)
            LockStatus.Active(active, durationMillis)
        } catch (failure: Throwable) {
            val releasing = session.copy(phase = LockPhase.RELEASING)
            val markerWrite = runCatching { store.write(releasing) }
            if (markerWrite.isFailure) {
                markerWrite.exceptionOrNull()?.let(failure::addSuppressed)
                // Without a durable RELEASING marker, releasing here could be undone
                // by recovery from the older ARMING/ACTIVE record. Keep the original
                // deadline authoritative and retry reconciliation instead.
                runCatching { policy.engage(targetPackage) }
                runCatching { scheduler.schedule(session) }
                runCatching { scheduler.scheduleRetry() }
                throw LockOperationException(
                    "L'état de retour arrière n'a pas pu être enregistré. Par sécurité, " +
                        "le verrou confirmé reste valable jusqu'à l'échéance initiale.",
                    failure,
                )
            }
            val rollback = runCatching {
                policy.release(targetPackage)
                scheduler.cancel()
                store.clear()
            }
            if (rollback.isFailure) {
                runCatching { scheduler.scheduleRetry() }
                failure.addSuppressed(rollback.exceptionOrNull()!!)
                throw LockOperationException(
                    "L'armement a échoué et Android n'a pas encore terminé le retour arrière. " +
                        "La cible reste protégée ; TempoLock réessaiera automatiquement.",
                    failure,
                )
            }
            throw LockOperationException(
                "Le verrou n'a pas été appliqué intégralement ; les modifications ont été annulées.",
                failure,
            )
        }
    }

    suspend fun reconcile(): LockStatus = mutex.withLock {
        if (!policy.isDeviceOwner()) return@withLock LockStatus.OwnerRequired

        var releaseAttempt = false
        try {
            val session = store.read()
            if (session == null) {
                policy.ensureSelfProtected()
                scheduler.cancel()
                return@withLock LockStatus.Idle
            }

            val shouldRelease = session.phase == LockPhase.RELEASING || session.isExpired(clock.now())
            if (shouldRelease) {
                releaseAttempt = true
                val releasing = session.copy(phase = LockPhase.RELEASING)
                if (releasing != session) store.write(releasing)
                policy.release(session.targetPackage)
                scheduler.cancel()
                store.clear()
                LockStatus.Idle
            } else {
                policy.ensureSelfProtected()
                policy.engage(session.targetPackage)
                val active = session.copy(phase = LockPhase.ACTIVE)
                if (active != session) store.write(active)
                scheduler.schedule(active)
                LockStatus.Active(active, active.remainingMillis(clock.now()))
            }
        } catch (failure: Throwable) {
            // This is deliberately the outermost boundary: read/write integrity,
            // DevicePolicyManager and alarm failures all get another attempt.
            runCatching { scheduler.scheduleRetry() }
            LockStatus.Fault(
                if (releaseAttempt) {
                    "L'échéance est atteinte, mais Android n'a pas encore confirmé le déblocage. " +
                        "TempoLock réessaiera automatiquement."
                } else {
                    "Le verrouillage reste fermé par précaution. TempoLock réessaiera " +
                        "automatiquement : " +
                        (failure.message ?: failure.javaClass.simpleName)
                },
            )
        }
    }

    fun currentStatus(): LockStatus {
        if (!policy.isDeviceOwner()) return LockStatus.OwnerRequired
        return try {
            val session = store.read() ?: return LockStatus.Idle
            if (session.phase == LockPhase.RELEASING) {
                return LockStatus.Fault(
                    "TempoLock attend la confirmation Android du déblocage et réessaie automatiquement.",
                )
            }
            val remaining = session.remainingMillis(clock.now())
            LockStatus.Active(session, remaining)
        } catch (failure: Throwable) {
            LockStatus.Fault(
                "L'état sécurisé ne peut pas être lu. L'application cible reste verrouillée par précaution.",
            )
        }
    }

    fun exactAlarmsAllowed(): Boolean = scheduler.exactAlarmsAllowed()
}
