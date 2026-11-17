package dev.saga.payment.adapter.`in`.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.saga.messaging.AuthorizePayment
import dev.saga.messaging.DemoFaults
import dev.saga.messaging.Envelope
import dev.saga.messaging.IdempotentMessageHandler
import dev.saga.messaging.MessageTypes
import dev.saga.messaging.RefundPayment
import dev.saga.messaging.Topology
import dev.saga.messaging.TraceEmitter
import dev.saga.messaging.TraceKinds
import dev.saga.messaging.payloadAs
import dev.saga.payment.application.port.`in`.AuthorizePaymentCommand
import dev.saga.payment.application.port.`in`.AuthorizePaymentUseCase
import dev.saga.payment.application.port.`in`.RefundPaymentCommand
import dev.saga.payment.application.port.`in`.RefundPaymentUseCase
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class PaymentCommandListener(
    private val mapper: ObjectMapper,
    private val handler: IdempotentMessageHandler,
    private val authorizePayment: AuthorizePaymentUseCase,
    private val refundPayment: RefundPaymentUseCase,
    private val trace: TraceEmitter,
) {
    @RabbitListener(queues = [Topology.PAYMENT_QUEUE])
    fun onCommand(body: String) {
        val envelope: Envelope = mapper.readValue(body)

        val delayMs = DemoFaults.from(envelope.headers).paymentDelayMs
        if (delayMs > 0 && envelope.type == MessageTypes.AUTHORIZE_PAYMENT) {
            trace.emit(envelope.sagaId, TraceKinds.CHAOS, envelope.type, envelope.messageId,
                "payment-service está lento para esta saga: su respuesta va a salir dentro de ${delayMs / 1000} s (falla inyectada)")
        }

        handler.handle(envelope) {
            when (envelope.type) {
                MessageTypes.AUTHORIZE_PAYMENT -> {
                    val cmd = envelope.payloadAs<AuthorizePayment>(mapper)
                    val payment = authorizePayment.authorize(
                        AuthorizePaymentCommand(envelope.sagaId, cmd.orderId, cmd.customerId, cmd.amount),
                    )
                    "Pago ${payment.id.take(8)} → ${payment.status}" + (payment.reason?.let { " ($it)" } ?: "")
                }
                MessageTypes.REFUND_PAYMENT -> {
                    val cmd = envelope.payloadAs<RefundPayment>(mapper)
                    val payment = refundPayment.refund(RefundPaymentCommand(envelope.sagaId, cmd.orderId))
                    "Compensación: pago de la orden ${cmd.orderId.take(8)} → ${payment?.status ?: "inexistente (no-op)"}"
                }
                else -> error("Comando desconocido: ${envelope.type}")
            }
        }
    }
}
