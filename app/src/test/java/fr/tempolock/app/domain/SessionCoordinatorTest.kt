package fr.tempolock.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCoordinatorTest {

    @Test
    fun `arm refuses to start when device owner is missing`() = runTest {
        val fixture = Fixture(owner = false)

        val failure = expectFailure(IllegalStateException::class.java) {
            fixture.coordinator.arm(TARGET_PACKAGE, TARGET_LABEL, TWO_MINUTES)
        }

        assertEquals("TempoLock n'est pas propriétaire de l'appareil.", failure.message)
        assertEquals(0, fixture.store.readCalls)
        assertNull(fixture.store.session)
        assertEquals(0, fixture.policy.prepareTrustedTimeCalls)
        assertTrue(fixture.policy.engagedPackages.isEmpty())
        assertTrue(fixture.scheduler.scheduledSessions.isEmpty())
        assertTrue(fixture.scheduler.retryDelays.isEmpty())
    }

    @Test
    fun `reconcile reports owner required without touching persisted state`() = runTest {
        val existing = session(phase = LockPhase.ACTIVE)
        val fixture = Fixture(owner = false, storedSession = existing)

        val status = fixture.coordinator.reconcile()

        assertSame(LockStatus.OwnerRequired, status)
        assertSame(existing, fixture.store.session)
        assertEquals(0, fixture.store.readCalls)
        assertEquals(0, fixture.policy.ensureSelfProtectedCalls)
        assertTrue(fixture.policy.engagedPackages.isEmpty())
        assertTrue(fixture.scheduler.scheduledSessions.isEmpty())
    }

    @Test
    fun `reconcile schedules retry when secure session read fails`() = runTest {
        val readFailure = IllegalStateException("secure session unreadable")
        val fixture = Fixture()
        fixture.store.readFailure = readFailure

        val status = fixture.coordinator.reconcile()

        assertEquals(
            LockStatus.Fault(
                "Le verrouillage reste fermé par précaution. TempoLock réessaiera " +
                    "automatiquement : secure session unreadable",
            ),
            status,
        )
        assertEquals(1, fixture.store.readCalls)
        assertTrue(fixture.store.writes.isEmpty())
        assertEquals(0, fixture.store.clearCalls)
        assertEquals(0, fixture.policy.ensureSelfProtectedCalls)
        assertTrue(fixture.policy.engagedPackages.isEmpty())
        assertTrue(fixture.policy.releasedPackages.isEmpty())
        assertEquals(0, fixture.scheduler.cancelCalls)
        assertEquals(listOf(DEFAULT_RETRY_DELAY), fixture.scheduler.retryDelays)
    }

    @Test
    fun `arm persists arming before applying policy then publishes active session`() = runTest {
        val now = TimeSnapshot(
            trustedEpochMillis = START_EPOCH,
            elapsedMillis = START_ELAPSED,
            bootCount = START_BOOT_COUNT,
        )
        val fixture = Fixture(now = now)

        val status = fixture.coordinator.arm(TARGET_PACKAGE, TARGET_LABEL, TWO_MINUTES)

        assertTrue(status is LockStatus.Active)
        val active = status as LockStatus.Active
        val expected = session(phase = LockPhase.ACTIVE)
        assertEquals(expected, active.session)
        assertEquals(TWO_MINUTES, active.remainingMillis)
        assertEquals(listOf(LockPhase.ARMING, LockPhase.ACTIVE), fixture.store.writes.map { it.phase })
        assertEquals(expected, fixture.store.session)
        assertEquals(1, fixture.policy.prepareTrustedTimeCalls)
        assertTrue(
            fixture.events.indexOf("policy.prepareTrustedTime") <
                fixture.events.indexOf("clock.now"),
        )
        assertEquals(listOf(TARGET_PACKAGE), fixture.policy.engagedPackages)
        assertEquals(listOf(expected), fixture.scheduler.scheduledSessions)
        assertTrue(fixture.policy.releasedPackages.isEmpty())
        assertEquals(0, fixture.scheduler.cancelCalls)
        assertEquals(0, fixture.store.clearCalls)
    }

    @Test
    fun `arm rejects durations outside the supported range before side effects`() = runTest {
        listOf(
            MIN_LOCK_DURATION_MILLIS - 1L,
            MAX_LOCK_DURATION_MILLIS + 1L,
        ).forEach { invalidDuration ->
            val fixture = Fixture()

            val failure = expectFailure(IllegalArgumentException::class.java) {
                fixture.coordinator.arm(TARGET_PACKAGE, TARGET_LABEL, invalidDuration)
            }

            assertEquals("La durée doit être comprise entre 1 minute et 30 jours.", failure.message)
            assertEquals(0, fixture.store.readCalls)
            assertNull(fixture.store.session)
            assertEquals(0, fixture.policy.prepareTrustedTimeCalls)
            assertTrue(fixture.policy.engagedPackages.isEmpty())
            assertTrue(fixture.scheduler.scheduledSessions.isEmpty())
        }
    }

    @Test
    fun `arm refuses exact alarm denial before trusted time or policy side effects`() = runTest {
        val fixture = Fixture(exactAlarmsAllowed = false)

        val failure = expectFailure(IllegalStateException::class.java) {
            fixture.coordinator.arm(TARGET_PACKAGE, TARGET_LABEL, TWO_MINUTES)
        }

        assertEquals(
            "L'autorisation des alarmes exactes est requise avant le verrouillage.",
            failure.message,
        )
        assertEquals(1, fixture.store.readCalls)
        assertTrue(fixture.store.writes.isEmpty())
        assertNull(fixture.store.session)
        assertEquals(1, fixture.scheduler.exactAlarmChecks)
        assertEquals(0, fixture.policy.prepareTrustedTimeCalls)
        assertEquals(0, fixture.clock.nowCalls)
        assertTrue(fixture.policy.engagedPackages.isEmpty())
        assertEquals(0, fixture.scheduler.scheduleAttempts)
        assertTrue(fixture.scheduler.retryDelays.isEmpty())
    }

    @Test
    fun `arm without trusted network time leaves store and policy untouched`() = runTest {
        val fixture = Fixture(
            now = TimeSnapshot(
                trustedEpochMillis = null,
                elapsedMillis = START_ELAPSED,
                bootCount = START_BOOT_COUNT,
            ),
        )

        val failure = expectFailure(TrustedTimeUnavailableException::class.java) {
            fixture.coordinator.arm(TARGET_PACKAGE, TARGET_LABEL, TWO_MINUTES)
        }

        assertEquals(
            "L'heure réseau Android n'est pas encore disponible. Connecte brièvement l'appareil " +
                "à Internet, puis réessaie : aucun verrou n'a été lancé.",
            failure.message,
        )
        assertEquals(1, fixture.store.readCalls)
        assertTrue(fixture.store.writes.isEmpty())
        assertEquals(0, fixture.store.clearCalls)
        assertNull(fixture.store.session)
        assertEquals(1, fixture.policy.prepareTrustedTimeCalls)
        assertEquals(1, fixture.clock.nowCalls)
        assertTrue(fixture.policy.engagedPackages.isEmpty())
        assertTrue(fixture.policy.releasedPackages.isEmpty())
        assertEquals(0, fixture.scheduler.scheduleAttempts)
        assertTrue(fixture.scheduler.retryDelays.isEmpty())
    }

    @Test
    fun `arm rolls back every partial change when scheduling fails`() = runTest {
        val schedulingFailure = IllegalStateException("alarm service unavailable")
        val fixture = Fixture(scheduleFailure = schedulingFailure)

        val failure = expectFailure(LockOperationException::class.java) {
            fixture.coordinator.arm(TARGET_PACKAGE, TARGET_LABEL, TWO_MINUTES)
        }

        assertSame(schedulingFailure, failure.cause)
        assertEquals(
            "Le verrou n'a pas été appliqué intégralement ; les modifications ont été annulées.",
            failure.message,
        )
        assertEquals(
            listOf(LockPhase.ARMING, LockPhase.ACTIVE, LockPhase.RELEASING),
            fixture.store.writes.map { it.phase },
        )
        assertEquals(listOf(TARGET_PACKAGE), fixture.policy.engagedPackages)
        assertEquals(listOf(TARGET_PACKAGE), fixture.policy.releasedPackages)
        assertEquals(1, fixture.scheduler.scheduleAttempts)
        assertEquals(1, fixture.scheduler.cancelCalls)
        assertEquals(1, fixture.store.clearCalls)
        assertNull(fixture.store.session)
        assertTrue(fixture.scheduler.retryDelays.isEmpty())
    }

    @Test
    fun `arm keeps releasing state and schedules retry when rollback release fails`() = runTest {
        val schedulingFailure = IllegalStateException("alarm service unavailable")
        val releaseFailure = IllegalStateException("Android still holds restrictions")
        val fixture = Fixture(scheduleFailure = schedulingFailure)
        fixture.policy.releaseFailure = releaseFailure

        val failure = expectFailure(LockOperationException::class.java) {
            fixture.coordinator.arm(TARGET_PACKAGE, TARGET_LABEL, TWO_MINUTES)
        }

        assertSame(schedulingFailure, failure.cause)
        assertEquals(
            "L'armement a échoué et Android n'a pas encore terminé le retour arrière. " +
                "La cible reste protégée ; TempoLock réessaiera automatiquement.",
            failure.message,
        )
        assertEquals(listOf(releaseFailure), schedulingFailure.suppressed.toList())
        assertEquals(
            listOf(LockPhase.ARMING, LockPhase.ACTIVE, LockPhase.RELEASING),
            fixture.store.writes.map { it.phase },
        )
        assertEquals(LockPhase.RELEASING, fixture.store.session?.phase)
        assertEquals(listOf(TARGET_PACKAGE), fixture.policy.releasedPackages)
        assertEquals(0, fixture.scheduler.cancelCalls)
        assertEquals(0, fixture.store.clearCalls)
        assertEquals(listOf(DEFAULT_RETRY_DELAY), fixture.scheduler.retryDelays)
    }

    @Test
    fun `arm preserves original session and never releases when releasing marker write fails`() = runTest {
        val schedulingFailure = IllegalStateException("alarm service unavailable")
        val markerFailure = IllegalStateException("secure storage write failed")
        val fixture = Fixture(scheduleFailure = schedulingFailure)
        fixture.store.writeFailurePhase = LockPhase.RELEASING
        fixture.store.writeFailure = markerFailure

        val failure = expectFailure(LockOperationException::class.java) {
            fixture.coordinator.arm(TARGET_PACKAGE, TARGET_LABEL, TWO_MINUTES)
        }

        assertSame(schedulingFailure, failure.cause)
        assertEquals(
            "L'état de retour arrière n'a pas pu être enregistré. Par sécurité, " +
                "le verrou confirmé reste valable jusqu'à l'échéance initiale.",
            failure.message,
        )
        assertEquals(listOf(markerFailure), schedulingFailure.suppressed.toList())
        assertEquals(
            listOf(LockPhase.ARMING, LockPhase.ACTIVE, LockPhase.RELEASING),
            fixture.store.writeAttempts,
        )
        assertEquals(listOf(LockPhase.ARMING, LockPhase.ACTIVE), fixture.store.writes.map { it.phase })
        assertEquals(LockPhase.ACTIVE, fixture.store.session?.phase)
        assertEquals(listOf(TARGET_PACKAGE, TARGET_PACKAGE), fixture.policy.engagedPackages)
        assertTrue(fixture.policy.releasedPackages.isEmpty())
        assertEquals(2, fixture.scheduler.scheduleAttempts)
        assertEquals(0, fixture.scheduler.cancelCalls)
        assertEquals(0, fixture.store.clearCalls)
        assertEquals(listOf(DEFAULT_RETRY_DELAY), fixture.scheduler.retryDelays)
    }

    @Test
    fun `a second arm cannot mutate an active immutable session`() = runTest {
        val fixture = Fixture()
        fixture.coordinator.arm(TARGET_PACKAGE, TARGET_LABEL, TWO_MINUTES)
        val originalSession = fixture.store.session
        val originalWrites = fixture.store.writes.toList()

        val failure = expectFailure(IllegalStateException::class.java) {
            fixture.coordinator.arm("com.example.other", "Other", MIN_LOCK_DURATION_MILLIS)
        }

        assertEquals("Un verrouillage est déjà actif.", failure.message)
        assertSame(originalSession, fixture.store.session)
        assertEquals(originalWrites, fixture.store.writes)
        assertEquals(listOf(TARGET_PACKAGE), fixture.policy.engagedPackages)
        assertEquals(1, fixture.scheduler.scheduleAttempts)
        assertEquals(1, fixture.policy.prepareTrustedTimeCalls)
        assertTrue(fixture.policy.releasedPackages.isEmpty())
        assertEquals(0, fixture.scheduler.cancelCalls)
    }

    @Test
    fun `reconcile before deadline preserves active session and remaining time`() = runTest {
        val existing = session(phase = LockPhase.ACTIVE)
        val now = TimeSnapshot(
            trustedEpochMillis = START_EPOCH + 30_000L,
            elapsedMillis = START_ELAPSED + 30_000L,
            bootCount = START_BOOT_COUNT,
        )
        val fixture = Fixture(now = now, storedSession = existing)

        val status = fixture.coordinator.reconcile()

        assertEquals(LockStatus.Active(existing, 90_000L), status)
        assertSame(existing, fixture.store.session)
        assertTrue(fixture.store.writes.isEmpty())
        assertEquals(1, fixture.policy.ensureSelfProtectedCalls)
        assertEquals(listOf(TARGET_PACKAGE), fixture.policy.engagedPackages)
        assertEquals(listOf(existing), fixture.scheduler.scheduledSessions)
        assertTrue(fixture.policy.releasedPackages.isEmpty())
        assertEquals(0, fixture.scheduler.cancelCalls)
    }

    @Test
    fun `reconcile at deadline releases once and remains idle on retry`() = runTest {
        val existing = session(phase = LockPhase.ACTIVE)
        val deadline = TimeSnapshot(
            trustedEpochMillis = existing.deadlineEpochMillis,
            elapsedMillis = existing.deadlineElapsedMillis,
            bootCount = existing.bootCountAtStart,
        )
        val fixture = Fixture(now = deadline, storedSession = existing)

        val first = fixture.coordinator.reconcile()
        val second = fixture.coordinator.reconcile()

        assertSame(LockStatus.Idle, first)
        assertSame(LockStatus.Idle, second)
        assertEquals(listOf(LockPhase.RELEASING), fixture.store.writes.map { it.phase })
        assertEquals(listOf(TARGET_PACKAGE), fixture.policy.releasedPackages)
        assertEquals(2, fixture.scheduler.cancelCalls)
        assertEquals(1, fixture.store.clearCalls)
        assertNull(fixture.store.session)
        assertEquals(1, fixture.policy.ensureSelfProtectedCalls)
        assertTrue(fixture.scheduler.retryDelays.isEmpty())
    }

    @Test
    fun `reconcile keeps releasing state and schedules retry when release fails`() = runTest {
        val existing = session(phase = LockPhase.ACTIVE)
        val deadline = TimeSnapshot(
            trustedEpochMillis = existing.deadlineEpochMillis,
            elapsedMillis = existing.deadlineElapsedMillis,
            bootCount = existing.bootCountAtStart,
        )
        val fixture = Fixture(now = deadline, storedSession = existing)
        fixture.policy.releaseFailure = IllegalStateException("release refused")

        val failed = fixture.coordinator.reconcile()

        assertEquals(
            LockStatus.Fault(
                "L'échéance est atteinte, mais Android n'a pas encore confirmé le déblocage. " +
                    "TempoLock réessaiera automatiquement.",
            ),
            failed,
        )
        assertEquals(LockPhase.RELEASING, fixture.store.session?.phase)
        assertEquals(listOf(TARGET_PACKAGE), fixture.policy.releasedPackages)
        assertEquals(0, fixture.scheduler.cancelCalls)
        assertEquals(0, fixture.store.clearCalls)
        assertEquals(listOf(DEFAULT_RETRY_DELAY), fixture.scheduler.retryDelays)

        fixture.policy.releaseFailure = null
        val recovered = fixture.coordinator.reconcile()

        assertSame(LockStatus.Idle, recovered)
        assertEquals(listOf(TARGET_PACKAGE, TARGET_PACKAGE), fixture.policy.releasedPackages)
        assertEquals(1, fixture.scheduler.cancelCalls)
        assertEquals(1, fixture.store.clearCalls)
        assertNull(fixture.store.session)
        assertEquals(listOf(DEFAULT_RETRY_DELAY), fixture.scheduler.retryDelays)
    }

    @Test
    fun `reconcile after reboot uses persisted wall clock deadline and rearms policy`() = runTest {
        val store = FakeSessionStore()
        val firstClock = FakeTrustedClock(
            TimeSnapshot(
                trustedEpochMillis = START_EPOCH,
                elapsedMillis = START_ELAPSED,
                bootCount = START_BOOT_COUNT,
            ),
        )
        val firstPolicy = FakeLockPolicy(owner = true)
        val firstScheduler = FakeUnlockScheduler()
        SessionCoordinator(store, firstClock, firstPolicy, firstScheduler)
            .arm(TARGET_PACKAGE, TARGET_LABEL, TWO_MINUTES)

        val rebootClock = FakeTrustedClock(
            TimeSnapshot(
                trustedEpochMillis = START_EPOCH + 30_000L,
                elapsedMillis = 5_000L,
                bootCount = START_BOOT_COUNT + 1,
            ),
        )
        val rebootPolicy = FakeLockPolicy(owner = true)
        val rebootScheduler = FakeUnlockScheduler()
        val restartedCoordinator = SessionCoordinator(store, rebootClock, rebootPolicy, rebootScheduler)

        val status = restartedCoordinator.reconcile()

        assertTrue(status is LockStatus.Active)
        assertEquals(90_000L, (status as LockStatus.Active).remainingMillis)
        assertEquals(listOf(TARGET_PACKAGE), rebootPolicy.engagedPackages)
        assertEquals(1, rebootScheduler.scheduleAttempts)
        assertEquals(LockPhase.ACTIVE, rebootScheduler.scheduledSessions.single().phase)
        assertEquals(store.session, rebootScheduler.scheduledSessions.single())
    }

    @Test
    fun `reconcile uses trusted epoch when elapsed time rolls back without boot count change`() = runTest {
        val existing = session(phase = LockPhase.ACTIVE)
        val suspiciousClock = TimeSnapshot(
            trustedEpochMillis = START_EPOCH + 30_000L,
            elapsedMillis = 5_000L,
            bootCount = START_BOOT_COUNT,
        )
        val fixture = Fixture(now = suspiciousClock, storedSession = existing)

        val status = fixture.coordinator.reconcile()

        assertEquals(LockStatus.Active(existing, 90_000L), status)
        assertEquals(2, fixture.clock.nowCalls)
        assertEquals(listOf(TARGET_PACKAGE), fixture.policy.engagedPackages)
        assertEquals(listOf(existing), fixture.scheduler.scheduledSessions)
        assertTrue(fixture.policy.releasedPackages.isEmpty())
        assertTrue(fixture.scheduler.retryDelays.isEmpty())
    }

    @Test
    fun `reconcile is idempotent while recovering an arming session`() = runTest {
        val interrupted = session(phase = LockPhase.ARMING)
        val now = TimeSnapshot(
            trustedEpochMillis = START_EPOCH + 10_000L,
            elapsedMillis = START_ELAPSED + 10_000L,
            bootCount = START_BOOT_COUNT,
        )
        val fixture = Fixture(now = now, storedSession = interrupted)

        val first = fixture.coordinator.reconcile()
        val second = fixture.coordinator.reconcile()

        val expected = interrupted.copy(phase = LockPhase.ACTIVE)
        assertEquals(LockStatus.Active(expected, 110_000L), first)
        assertEquals(first, second)
        assertEquals(listOf(expected), fixture.store.writes)
        assertEquals(expected, fixture.store.session)
        assertEquals(listOf(TARGET_PACKAGE, TARGET_PACKAGE), fixture.policy.engagedPackages)
        assertEquals(listOf(expected, expected), fixture.scheduler.scheduledSessions)
        assertTrue(fixture.policy.releasedPackages.isEmpty())
        assertEquals(0, fixture.scheduler.cancelCalls)
        assertEquals(0, fixture.store.clearCalls)
    }

    private class Fixture(
        owner: Boolean = true,
        now: TimeSnapshot = TimeSnapshot(START_EPOCH, START_ELAPSED, START_BOOT_COUNT),
        storedSession: LockSession? = null,
        scheduleFailure: Throwable? = null,
        exactAlarmsAllowed: Boolean = true,
    ) {
        val events = mutableListOf<String>()
        val store = FakeSessionStore(storedSession)
        val clock = FakeTrustedClock(now, events)
        val policy = FakeLockPolicy(owner, events)
        val scheduler = FakeUnlockScheduler(scheduleFailure, exactAlarmsAllowed)
        val coordinator = SessionCoordinator(store, clock, policy, scheduler)
    }

    private class FakeSessionStore(initial: LockSession? = null) : SessionStore {
        var session: LockSession? = initial
        val writes = mutableListOf<LockSession>()
        val writeAttempts = mutableListOf<LockPhase>()
        var readCalls = 0
        var clearCalls = 0
        var readFailure: Throwable? = null
        var writeFailurePhase: LockPhase? = null
        var writeFailure: Throwable? = null

        override fun read(): LockSession? {
            readCalls += 1
            readFailure?.let { throw it }
            return session
        }

        override fun write(session: LockSession) {
            writeAttempts += session.phase
            if (session.phase == writeFailurePhase) {
                throw writeFailure ?: IllegalStateException("configured write failure")
            }
            writes += session
            this.session = session
        }

        override fun clear() {
            clearCalls += 1
            session = null
        }
    }

    private class FakeTrustedClock(
        var snapshot: TimeSnapshot,
        private val events: MutableList<String> = mutableListOf(),
    ) : TrustedClock {
        var nowCalls = 0

        override fun now(): TimeSnapshot {
            nowCalls += 1
            events += "clock.now"
            return snapshot
        }
    }

    private class FakeLockPolicy(
        var owner: Boolean,
        private val events: MutableList<String> = mutableListOf(),
    ) : LockPolicy {
        var suspended = true
        var engageFailure: Throwable? = null
        var releaseFailure: Throwable? = null
        var prepareTrustedTimeCalls = 0
        var ensureSelfProtectedCalls = 0
        val engagedPackages = mutableListOf<String>()
        val releasedPackages = mutableListOf<String>()

        override fun isDeviceOwner(): Boolean = owner

        override fun prepareTrustedTime() {
            prepareTrustedTimeCalls += 1
            events += "policy.prepareTrustedTime"
        }

        override fun ensureSelfProtected() {
            ensureSelfProtectedCalls += 1
        }

        override fun engage(targetPackage: String) {
            engagedPackages += targetPackage
            engageFailure?.let { throw it }
        }

        override fun release(targetPackage: String) {
            releasedPackages += targetPackage
            releaseFailure?.let { throw it }
        }

        override fun isSuspended(targetPackage: String): Boolean = suspended
    }

    private class FakeUnlockScheduler(
        private val scheduleFailure: Throwable? = null,
        private val exactAlarmsAllowedValue: Boolean = true,
    ) : UnlockScheduler {
        val scheduledSessions = mutableListOf<LockSession>()
        var scheduleAttempts = 0
        var cancelCalls = 0
        var exactAlarmChecks = 0
        val retryDelays = mutableListOf<Long>()

        override fun schedule(session: LockSession) {
            scheduleAttempts += 1
            scheduleFailure?.let { throw it }
            scheduledSessions += session
        }

        override fun scheduleRetry(delayMillis: Long) {
            retryDelays += delayMillis
        }

        override fun cancel() {
            cancelCalls += 1
        }

        override fun exactAlarmsAllowed(): Boolean {
            exactAlarmChecks += 1
            return exactAlarmsAllowedValue
        }
    }

    private suspend fun <T : Throwable> expectFailure(
        expectedType: Class<T>,
        block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (failure: Throwable) {
            if (expectedType.isInstance(failure)) return expectedType.cast(failure)
            throw AssertionError(
                "Expected ${expectedType.simpleName}, but caught ${failure.javaClass.simpleName}",
                failure,
            )
        }
        throw AssertionError("Expected ${expectedType.simpleName}, but no exception was thrown")
    }

    companion object {
        private const val TARGET_PACKAGE = "com.snapchat.android"
        private const val TARGET_LABEL = "Snapchat"
        private const val START_EPOCH = 1_700_000_000_000L
        private const val START_ELAPSED = 500_000L
        private const val START_BOOT_COUNT = 7
        private const val TWO_MINUTES = 120_000L
        private const val DEFAULT_RETRY_DELAY = 60_000L

        private fun session(phase: LockPhase): LockSession = LockSession(
            targetPackage = TARGET_PACKAGE,
            targetLabel = TARGET_LABEL,
            durationMillis = TWO_MINUTES,
            startedAtEpochMillis = START_EPOCH,
            startedAtElapsedMillis = START_ELAPSED,
            bootCountAtStart = START_BOOT_COUNT,
            deadlineEpochMillis = START_EPOCH + TWO_MINUTES,
            deadlineElapsedMillis = START_ELAPSED + TWO_MINUTES,
            phase = phase,
        )
    }
}
