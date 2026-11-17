package dev.saga.payment.application.port.`in`

import dev.saga.payment.domain.Payment
import java.math.BigDecimal

data class AuthorizePaymentCommand(
    val sagaId: String,
    val orderId: String,
    val customerId: String,
    val amount: BigDecimal,
)

data class RefundPaymentCommand(val sagaId: String, val orderId: String)

interface AuthorizePaymentUseCase {
    fun authorize(command: AuthorizePaymentCommand): Payment
}

interface RefundPaymentUseCase {
    fun refund(command: RefundPaymentCommand): Payment?
}

interface ListPaymentsQuery {
    fun listPayments(): List<Payment>
}
