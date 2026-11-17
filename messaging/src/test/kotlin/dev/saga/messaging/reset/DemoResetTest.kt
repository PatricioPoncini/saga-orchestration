package dev.saga.messaging.reset

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class DemoResetTest {
    private val every15 = Duration.ofMinutes(15)

    @Test
    fun `aligns to wall-clock boundaries so every service resets at the same time`() {
        assertEquals(Instant.parse("2026-01-01T10:15:00Z"), DemoReset.nextBoundary(Instant.parse("2026-01-01T10:03:27Z"), every15))
        assertEquals(Instant.parse("2026-01-01T10:15:00Z"), DemoReset.nextBoundary(Instant.parse("2026-01-01T10:14:58.500Z"), every15))
    }

    @Test
    fun `does not fire the same boundary twice when the job runs a few ms early`() {
        assertEquals(Instant.parse("2026-01-01T10:30:00Z"), DemoReset.nextBoundary(Instant.parse("2026-01-01T10:14:59.995Z"), every15))
        assertEquals(Instant.parse("2026-01-01T10:30:00Z"), DemoReset.nextBoundary(Instant.parse("2026-01-01T10:15:00.010Z"), every15))
    }
}
