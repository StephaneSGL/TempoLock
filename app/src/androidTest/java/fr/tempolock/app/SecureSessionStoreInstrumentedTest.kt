package fr.tempolock.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.tempolock.app.data.SecureSessionStore
import fr.tempolock.app.domain.LockPhase
import fr.tempolock.app.domain.LockSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecureSessionStoreInstrumentedTest {
    private lateinit var protectedContext: Context
    private lateinit var store: SecureSessionStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        protectedContext = context.createDeviceProtectedStorageContext()
        removeJournals()
        store = SecureSessionStore(context)
    }

    @After
    fun tearDown() {
        removeJournals()
    }

    @Test
    fun signedSessionRoundTrips() {
        val expected = session()

        store.write(expected)

        assertEquals(expected, store.read())
    }

    @Test
    fun oneCorruptedJournalFallsBackToTheOther() {
        val expected = session()
        store.write(expected)
        journal(FILE_NAME_A).writeBytes(byteArrayOf(0x00, 0x01, 0x02))

        assertEquals(expected, store.read())
    }

    @Test
    fun newerSignedTombstoneBeatsAnOlderValidJournal() {
        store.write(session())
        val olderRecord = journal(FILE_NAME_A).readBytes()

        store.clear()
        journal(FILE_NAME_A).writeBytes(olderRecord)

        assertNull(store.read())
    }

    private fun removeJournals() {
        listOf(FILE_NAME_A, FILE_NAME_B).forEach { name ->
            listOf(name, "$name.bak", "$name.new").forEach { candidate ->
                protectedContext.filesDir.resolve(candidate).delete()
            }
        }
    }

    private fun journal(name: String): File = protectedContext.filesDir.resolve(name)

    private fun session(): LockSession = LockSession(
        targetPackage = "fr.tempolock.testtarget",
        targetLabel = "Cible de test",
        durationMillis = 120_000L,
        startedAtEpochMillis = 1_700_000_000_000L,
        startedAtElapsedMillis = 500_000L,
        bootCountAtStart = 7,
        deadlineEpochMillis = 1_700_000_120_000L,
        deadlineElapsedMillis = 620_000L,
        phase = LockPhase.ACTIVE,
    )

    private companion object {
        const val FILE_NAME_A = "active_lock_a.tlk"
        const val FILE_NAME_B = "active_lock_b.tlk"
    }
}
