package dev.saga.payment.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals

class PaymentTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `authorizes amounts within the credit limit`() {
        val p = Payment.authorize("p", "o", "c", BigDecimal("999"), forceRejection = false, now = now)
        assertEquals(PaymentStatus.AUTHORIZED, p.status)
    }

    @Test
    fun `rejects amounts above the credit limit`() {
        val p = Payment.authorize("p", "o", "c", BigDecimal("1350"), forceRejection = false, now = now)
        assertEquals(PaymentStatus.REJECTED, p.status)
    }

    @Test
    fun `refund is idempotent`() {
        val p = Payment.authorize("p", "o", "c", BigDecimal("10"), false, now).refund(now)
        assertEquals(PaymentStatus.REFUNDED, p.status)
        assertEquals(p, p.refund(now))
    }
}
