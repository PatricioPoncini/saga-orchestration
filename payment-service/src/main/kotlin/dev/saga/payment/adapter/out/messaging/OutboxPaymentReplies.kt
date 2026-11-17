package dev.saga.payment.adapter.out.messaging

import dev.saga.messaging.CommandReply
import dev.saga.messaging.MessageContext
import dev.saga.messaging.MessageTypes
import dev.saga.messaging.Outbox
import dev.saga.messaging.Topology
import dev.saga.payment.application.port.out.PaymentReplyPort
import dev.saga.payment.domain.Payment
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OutboxPaymentReplies(private val outbox: Outbox) : PaymentReplyPort {
    override fun paymentAuthorized(sagaId: String, payment: Payment) = reply(
        sagaId,
        MessageTypes.PAYMENT_AUTHORIZED,
        CommandReply(MessageTypes.AUTHORIZE_PAYMENT, success = true, data = mapOf("paymentId" to payment.id)),
        slowDown = true,
    )

    override fun paymentRejected(sagaId: String, payment: Payment) = reply(
        sagaId,
        MessageTypes.PAYMENT_REJECTED,
        CommandReply(MessageTypes.AUTHORIZE_PAYMENT, success = false, reason = payment.reason),
        slowDown = true,
    )

    override fun paymentRefunded(sagaId: String, orderId: String) = reply(
        sagaId,
        MessageTypes.PAYMENT_REFUNDED,
        CommandReply(MessageTypes.REFUND_PAYMENT, success = true),
    )

    private fun reply(sagaId: String, type: String, reply: CommandReply, slowDown: Boolean = false) {
        val delay = if (slowDown) Duration.ofMillis(MessageContext.faults().paymentDelayMs) else Duration.ZERO
        outbox.send(Topology.REPLIES_EXCHANGE, Topology.ORCHESTRATOR, sagaId, type, reply, delay = delay)
    }
}
