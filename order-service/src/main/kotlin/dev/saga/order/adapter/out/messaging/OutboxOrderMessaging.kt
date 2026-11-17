package dev.saga.order.adapter.out.messaging

import dev.saga.messaging.CommandReply
import dev.saga.messaging.MessageTypes
import dev.saga.messaging.Outbox
import dev.saga.messaging.StartCreateOrderSaga
import dev.saga.messaging.Topology
import dev.saga.order.application.port.out.OrderReplyPort
import dev.saga.order.application.port.out.SagaStarterPort
import dev.saga.order.domain.Order
import org.springframework.stereotype.Component

@Component
class OutboxSagaStarter(private val outbox: Outbox) : SagaStarterPort {
    override fun startCreateOrderSaga(order: Order) {
        outbox.send(
            Topology.COMMANDS_EXCHANGE, Topology.ORCHESTRATOR, order.sagaId, MessageTypes.START_CREATE_ORDER_SAGA,
            StartCreateOrderSaga(order.id, order.customerId, order.productId, order.quantity, order.amount),
        )
    }
}

@Component
class OutboxOrderReplies(private val outbox: Outbox) : OrderReplyPort {
    override fun orderApproved(sagaId: String, order: Order) {
        outbox.send(
            Topology.REPLIES_EXCHANGE, Topology.ORCHESTRATOR, sagaId, MessageTypes.ORDER_APPROVED,
            CommandReply(MessageTypes.APPROVE_ORDER, success = true),
        )
    }

    override fun orderRejected(sagaId: String, order: Order) {
        outbox.send(
            Topology.REPLIES_EXCHANGE, Topology.ORCHESTRATOR, sagaId, MessageTypes.ORDER_REJECTED,
            CommandReply(MessageTypes.REJECT_ORDER, success = true),
        )
    }
}
