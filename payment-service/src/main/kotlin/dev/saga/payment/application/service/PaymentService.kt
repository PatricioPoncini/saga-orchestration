package dev.saga.payment.application.service

import dev.saga.payment.application.port.`in`.AuthorizePaymentCommand
import dev.saga.payment.application.port.`in`.AuthorizePaymentUseCase
import dev.saga.payment.application.port.`in`.ListPaymentsQuery
import dev.saga.payment.application.port.`in`.RefundPaymentCommand
import dev.saga.payment.application.port.`in`.RefundPaymentUseCase
import dev.saga.payment.application.port.out.FailureInjectionPort
import dev.saga.payment.application.port.out.PaymentReplyPort
import dev.saga.payment.application.port.out.PaymentRepository
import dev.saga.payment.domain.Payment
import dev.saga.payment.domain.PaymentStatus
import java.time.Clock
import java.util.UUID

class PaymentService(
    private val payments: PaymentRepository,
    private val replies: PaymentReplyPort,
    private val failures: FailureInjectionPort,
    private val clock: Clock,
) : AuthorizePaymentUseCase, RefundPaymentUseCase, ListPaymentsQuery {
    override fun authorize(command: AuthorizePaymentCommand): Payment {
        val payment = payments.findByOrderId(command.orderId)
            ?: Payment.authorize(
                id = UUID.randomUUID().toString(),
                orderId = command.orderId,
                customerId = command.customerId,
                amount = command.amount,
                forceRejection = failures.forceRejection(),
                now = clock.instant(),
            ).also(payments::save)

        if (payment.status == PaymentStatus.REJECTED) {
            replies.paymentRejected(command.sagaId, payment)
        } else {
            replies.paymentAuthorized(command.sagaId, payment)
        }
        return payment
    }

    override fun refund(command: RefundPaymentCommand): Payment? {
        val refunded = payments.findByOrderId(command.orderId)
            ?.refund(clock.instant())
            ?.also(payments::save)
        replies.paymentRefunded(command.sagaId, command.orderId)
        return refunded
    }

    override fun listPayments(): List<Payment> = payments.findAll()
}
