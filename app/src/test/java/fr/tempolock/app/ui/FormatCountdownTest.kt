package fr.tempolock.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormatCountdownTest {

    @Test
    fun `countdown clamps negative values and rounds positive milliseconds up`() {
        assertEquals("00:00:00", formatCountdown(-1L))
        assertEquals("00:00:00", formatCountdown(0L))
        assertEquals("00:00:01", formatCountdown(1L))
        assertEquals("00:00:01", formatCountdown(999L))
        assertEquals("00:00:01", formatCountdown(1_000L))
        assertEquals("00:00:02", formatCountdown(1_001L))
    }

    @Test
    fun `countdown formats hours and days with fixed width time fields`() {
        assertEquals("01:01:01", formatCountdown(3_661_000L))
        assertEquals("23:59:59", formatCountdown(86_399_000L))
        assertEquals("1j 00:00:00", formatCountdown(86_400_000L))
        assertEquals("30j 00:00:00", formatCountdown(MAX_LOCK_DURATION_MILLIS))
    }

    @Test
    fun `deadline uses the explicit time zone`() {
        val deadline = 1_788_007_200_000L

        assertEquals(
            "Samedi 29 août 2026 à 14:40",
            formatDeadline(deadline, ZoneId.of("Europe/Paris")),
        )
        assertEquals(
            "Samedi 29 août 2026 à 12:40",
            formatDeadline(deadline, ZoneId.of("UTC")),
        )
    }

    private companion object {
        const val MAX_LOCK_DURATION_MILLIS = 30L * 24L * 60L * 60L * 1_000L
    }
}
