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
        PaymentStatus.AUTHORIZED -> copy(status = PaymentStatus.REFUNDED, reason = "Refunded by compensation", updatedAt = now)
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
                forceRejection -> "Card declined by the bank (injected fault)"
                amount > CREDIT_LIMIT -> "Amount $amount is over the credit limit of $CREDIT_LIMIT"
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
