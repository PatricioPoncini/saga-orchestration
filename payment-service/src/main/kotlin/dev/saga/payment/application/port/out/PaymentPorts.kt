package dev.saga.payment.application.port.out

import dev.saga.payment.domain.Payment

interface PaymentRepository {
    fun findByOrderId(orderId: String): Payment?
    fun save(payment: Payment)
    fun findAll(): List<Payment>
}

interface PaymentReplyPort {
    fun paymentAuthorized(sagaId: String, payment: Payment)
    fun paymentRejected(sagaId: String, payment: Payment)
    fun paymentRefunded(sagaId: String, orderId: String)
}

interface FailureInjectionPort {
    fun forceRejection(): Boolean
}
