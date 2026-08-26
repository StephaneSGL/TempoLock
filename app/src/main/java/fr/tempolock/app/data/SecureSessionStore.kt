package fr.tempolock.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.tempolock.app.domain.LockPhase
import fr.tempolock.app.domain.LockSession
import fr.tempolock.app.domain.MAX_LOCK_DURATION_MILLIS
import fr.tempolock.app.domain.MIN_LOCK_DURATION_MILLIS
import fr.tempolock.app.domain.SessionIntegrityException
import fr.tempolock.app.domain.SessionStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) : SessionStore {
    private val protectedContext = context.createDeviceProtectedStorageContext()
    private val journals = listOf(
        AtomicFile(protectedContext.filesDir.resolve(FILE_NAME_A)),
        AtomicFile(protectedContext.filesDir.resolve(FILE_NAME_B)),
    )

    @Synchronized
    override fun read(): LockSession? = readLatest().session

    @Synchronized
    override fun write(session: LockSession) {
        validate(session)
        writeNext(session)
    }

    @Synchronized
    override fun clear() {
        // A signed tombstone prevents an older surviving journal from resurrecting
        // a lock after a partial storage failure.
        writeNext(session = null)
    }

    private fun writeNext(session: LockSession?) {
        val current = readLatest()
        val next = StoredRecord(
            generation = Math.addExact(current.generation, 1L),
            session = session,
        )
        val failures = journals.mapNotNull { journal ->
            runCatching { writeRecord(journal, next) }.exceptionOrNull()
        }
        if (failures.size == journals.size) {
            val error = SessionIntegrityException(
                "Impossible d'enregistrer l'état sécurisé dans les deux journaux.",
                failures.first(),
            )
            failures.drop(1).forEach(error::addSuppressed)
            throw error
        }
    }

    private fun readLatest(): StoredRecord {
        val results = journals.map(::readRecord)
        val valid = results.filterIsInstance<JournalRead.Valid>().map { it.record }
        if (valid.isNotEmpty()) {
            val latestGeneration = valid.maxOf { it.generation }
            val latest = valid.filter { it.generation == latestGeneration }.distinct()
            if (latest.size != 1) {
                throw SessionIntegrityException(
                    "Les journaux TempoLock valides se contredisent à la même génération.",
                )
            }
            return latest.single()
        }

        if (results.all { it is JournalRead.Missing }) return StoredRecord(0L, null)

        val causes = results.filterIsInstance<JournalRead.Invalid>().map { it.cause }
        val error = SessionIntegrityException(
            "Aucun journal TempoLock valide n'est disponible ; le verrou reste fermé.",
            causes.firstOrNull(),
        )
        causes.drop(1).forEach(error::addSuppressed)
        throw error
    }

    private fun readRecord(file: AtomicFile): JournalRead {
        val stream = try {
            file.openRead()
        } catch (_: FileNotFoundException) {
            return JournalRead.Missing
        } catch (failure: Throwable) {
            return JournalRead.Invalid(failure)
        }

        return try {
            val record = DataInputStream(stream.buffered()).use { input ->
                check(input.readInt() == FILE_MAGIC) { "En-tête TempoLock invalide." }
                val payloadSize = input.readInt()
                check(payloadSize in 1..MAX_RECORD_BYTES) { "Taille d'état invalide." }
                val payload = ByteArray(payloadSize).also(input::readFully)
                val signatureSize = input.readInt()
                check(signatureSize in 16..128) { "Taille de signature invalide." }
                val signature = ByteArray(signatureSize).also(input::readFully)
                check(input.read() == -1) { "Données inattendues après l'état sécurisé." }
                check(MessageDigest.isEqual(signature, sign(payload))) {
                    "La signature de l'état ne correspond pas."
                }
                decode(payload)
            }
            JournalRead.Valid(record)
        } catch (failure: Throwable) {
            JournalRead.Invalid(failure)
        }
    }

    private fun writeRecord(file: AtomicFile, record: StoredRecord) {
        val payload = encode(record)
        check(payload.size in 1..MAX_RECORD_BYTES) { "L'état TempoLock dépasse la taille autorisée." }
        val signature = sign(payload)
        val stream = file.startWrite()
        try {
            val output = DataOutputStream(stream)
            output.writeInt(FILE_MAGIC)
            output.writeInt(payload.size)
            output.write(payload)
            output.writeInt(signature.size)
            output.write(signature)
            output.flush()
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    private fun encode(record: StoredRecord): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(JOURNAL_SCHEMA_VERSION)
                output.writeLong(record.generation)
                output.writeBoolean(record.session != null)
                record.session?.let { session ->
                    output.writeInt(session.schemaVersion)
                    output.writeUTF(session.targetPackage)
                    output.writeUTF(session.targetLabel)
                    output.writeLong(session.durationMillis)
                    output.writeLong(session.startedAtEpochMillis)
                    output.writeLong(session.startedAtElapsedMillis)
                    output.writeInt(session.bootCountAtStart)
                    output.writeLong(session.deadlineEpochMillis)
                    output.writeLong(session.deadlineElapsedMillis)
                    output.writeUTF(session.phase.name)
                }
            }
            bytes.toByteArray()
        }

    private fun decode(payload: ByteArray): StoredRecord =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            check(input.readInt() == JOURNAL_SCHEMA_VERSION) {
                "Version de journal non prise en charge."
            }
            val generation = input.readLong()
            check(generation >= 1L) { "Génération de journal invalide." }
            val session = if (input.readBoolean()) {
                LockSession(
                    schemaVersion = input.readInt(),
                    targetPackage = input.readUTF(),
                    targetLabel = input.readUTF(),
                    durationMillis = input.readLong(),
                    startedAtEpochMillis = input.readLong(),
                    startedAtElapsedMillis = input.readLong(),
                    bootCountAtStart = input.readInt(),
                    deadlineEpochMillis = input.readLong(),
                    deadlineElapsedMillis = input.readLong(),
                    phase = LockPhase.valueOf(input.readUTF()),
                ).also(::validate)
            } else {
                null
            }
            check(input.available() == 0) { "Charge utile TempoLock invalide." }
            StoredRecord(generation, session)
        }

    private fun validate(session: LockSession) {
        check(session.schemaVersion == SESSION_SCHEMA_VERSION) {
            "Version d'état non prise en charge."
        }
        check(session.targetPackage.length in 3..255 && PACKAGE_NAME.matches(session.targetPackage)) {
            "Nom de paquet cible invalide."
        }
        check(session.targetLabel.isNotBlank() && session.targetLabel.length <= 200) {
            "Libellé cible invalide."
        }
        check(session.durationMillis in MIN_LOCK_DURATION_MILLIS..MAX_LOCK_DURATION_MILLIS) {
            "Durée persistée invalide."
        }
        check(session.startedAtEpochMillis > 0L) { "Heure de départ invalide." }
        check(session.startedAtElapsedMillis >= 0L) { "Chronomètre de départ invalide." }
        check(session.bootCountAtStart >= 0) { "Compteur de démarrage invalide." }
        check(
            Math.addExact(session.startedAtEpochMillis, session.durationMillis) ==
                session.deadlineEpochMillis,
        ) { "Échéance réseau incohérente." }
        check(
            Math.addExact(session.startedAtElapsedMillis, session.durationMillis) ==
                session.deadlineElapsedMillis,
        ) { "Échéance monotone incohérente." }
    }

    private fun sign(payload: ByteArray): ByteArray =
        Mac.getInstance(HMAC_ALGORITHM).run {
            init(loadOrCreateKey())
            doFinal(payload)
        }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(HMAC_ALGORITHM, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUnlockedDeviceRequired(false)
                    .build(),
            )
            generateKey()
        }
    }

    private data class StoredRecord(
        val generation: Long,
        val session: LockSession?,
    )

    private sealed interface JournalRead {
        data object Missing : JournalRead
        data class Valid(val record: StoredRecord) : JournalRead
        data class Invalid(val cause: Throwable) : JournalRead
    }

    private companion object {
        const val FILE_NAME_A = "active_lock_a.tlk"
        const val FILE_NAME_B = "active_lock_b.tlk"
        const val FILE_MAGIC = 0x544C4B32
        const val JOURNAL_SCHEMA_VERSION = 2
        const val SESSION_SCHEMA_VERSION = 1
        const val MAX_RECORD_BYTES = 16 * 1024
        const val KEY_ALIAS = "tempolock.session.hmac.v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val HMAC_ALGORITHM = "HmacSHA256"
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
    }
}
