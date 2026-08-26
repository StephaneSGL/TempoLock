package fr.tempolock.app.domain

interface SessionStore {
    fun read(): LockSession?
    fun write(session: LockSession)
    fun clear()
}

interface TrustedClock {
    fun now(): TimeSnapshot
}

interface LockPolicy {
    fun isDeviceOwner(): Boolean
    fun prepareTrustedTime()
    fun ensureSelfProtected()
    fun engage(targetPackage: String)
    fun release(targetPackage: String)
    fun isSuspended(targetPackage: String): Boolean
}

interface UnlockScheduler {
    fun schedule(session: LockSession)
    fun scheduleRetry(delayMillis: Long = 60_000L)
    fun cancel()
    fun exactAlarmsAllowed(): Boolean
}

class LockOperationException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class SessionIntegrityException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class TrustedTimeUnavailableException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
