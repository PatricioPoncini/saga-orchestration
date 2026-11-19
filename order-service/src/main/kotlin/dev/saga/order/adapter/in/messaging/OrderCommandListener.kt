package dev.saga.order.adapter.`in`.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.saga.messaging.ApproveOrder
import dev.saga.messaging.Envelope
import dev.saga.messaging.IdempotentMessageHandler
import dev.saga.messaging.MessageTypes
import dev.saga.messaging.RejectOrder
import dev.saga.messaging.Topology
import dev.saga.messaging.payloadAs
import dev.saga.order.application.port.`in`.ApproveOrderUseCase
import dev.saga.order.application.port.`in`.RejectOrderUseCase
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class OrderCommandListener(
    private val mapper: ObjectMapper,
    private val handler: IdempotentMessageHandler,
    private val approveOrder: ApproveOrderUseCase,
    private val rejectOrder: RejectOrderUseCase,
) {
    @RabbitListener(queues = [Topology.ORDER_QUEUE])
    fun onCommand(body: String) {
        val envelope: Envelope = mapper.readValue(body)
        handler.handle(envelope) {
            when (envelope.type) {
                MessageTypes.APPROVE_ORDER -> {
                    val cmd = envelope.payloadAs<ApproveOrder>(mapper)
                    val order = approveOrder.approve(envelope.sagaId, cmd.orderId)
                    "Order ${order.id.take(8)} → ${order.status}"
                }
                MessageTypes.REJECT_ORDER -> {
                    val cmd = envelope.payloadAs<RejectOrder>(mapper)
                    val order = rejectOrder.reject(envelope.sagaId, cmd.orderId, cmd.reason)
                    "Compensation: order ${order.id.take(8)} → ${order.status}"
                }
                else -> error("Unknown command: ${envelope.type}")
            }
        }
    }
}
