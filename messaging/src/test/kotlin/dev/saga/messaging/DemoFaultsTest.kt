package dev.saga.messaging

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoFaultsTest {
    @Test
    fun `faults travel as headers and come back the same`() {
        val faults = DemoFaults(paymentRejected = true, paymentDelayMs = 14_000, inventoryDown = true, duplicateCommands = true)
        assertEquals(faults, DemoFaults.from(faults.toHeaders()))
    }

    @Test
    fun `no faults means no headers`() {
        assertTrue(DemoFaults().toHeaders().isEmpty())
        assertEquals(DemoFaults(), DemoFaults.from(emptyMap()))
    }

    @Test
    fun `payment delay is capped`() {
        assertEquals(DemoFaults.MAX_DELAY_MS, DemoFaults.from(DemoFaults(paymentDelayMs = 999_999).toHeaders()).paymentDelayMs)
        assertEquals(DemoFaults.MAX_DELAY_MS, DemoFaults.from(mapOf(DemoFaults.PAYMENT_DELAY_MS to "999999")).paymentDelayMs)
    }

    @Test
    fun `message context is scoped and restored`() {
        assertTrue(MessageContext.headers().isEmpty())
        val outer = mapOf("a" to "1")
        MessageContext.with(outer) {
            MessageContext.with(mapOf("b" to "2")) { assertEquals(mapOf("b" to "2"), MessageContext.headers()) }
            assertEquals(outer, MessageContext.headers())
        }
        assertTrue(MessageContext.headers().isEmpty())
    }
}
