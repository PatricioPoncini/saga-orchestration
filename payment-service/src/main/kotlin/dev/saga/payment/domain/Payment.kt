package dev.saga.payment.domain

import java.math.BigDecimal
import java.time.Instant

enum class PaymentStatus { AUTHORIZED, REJECTED, REFUNDED }

data class Payment(
    val id: String,
    val orderId: String,
    val customerId: String,
    val amount: BigDecimal,
    val status: PaymentStatus,
    val reason: String?,
    val updatedAt: Instant,
) {
    fun refund(now: Instant): Payment = when (status) {
        PaymentStatus.AUTHORIZED -> copy(status = PaymentStatus.REFUNDED, reason = "Reembolsado por compensación", updatedAt = now)
        else -> this
    }

    companion object {
        val CREDIT_LIMIT: BigDecimal = BigDecimal("1000")

        fun authorize(
            id: String,
            orderId: String,
            customerId: String,
            amount: BigDecimal,
            forceRejection: Boolean,
            now: Instant,
        ): Payment {
            val rejectionReason = when {
                forceRejection -> "Tarjeta rechazada por el banco (falla inyectada)"
                amount > CREDIT_LIMIT -> "El monto $amount supera el límite de crédito de $CREDIT_LIMIT"
                else -> null
            }
            return Payment(
                id = id,
                orderId = orderId,
                customerId = customerId,
                amount = amount,
                status = if (rejectionReason == null) PaymentStatus.AUTHORIZED else PaymentStatus.REJECTED,
                reason = rejectionReason,
                updatedAt = now,
            )
        }
    }
}
