package dev.saga.inventory.adapter.out.messaging

import dev.saga.inventory.application.port.out.InventoryReplyPort
import dev.saga.inventory.domain.Reservation
import dev.saga.messaging.CommandReply
import dev.saga.messaging.MessageTypes
import dev.saga.messaging.Outbox
import dev.saga.messaging.Topology
import org.springframework.stereotype.Component

@Component
class OutboxInventoryReplies(private val outbox: Outbox) : InventoryReplyPort {
    override fun stockReserved(sagaId: String, reservation: Reservation) {
        outbox.send(
            Topology.REPLIES_EXCHANGE, Topology.ORCHESTRATOR, sagaId, MessageTypes.STOCK_RESERVED,
            CommandReply(MessageTypes.RESERVE_STOCK, success = true),
        )
    }

    override fun stockUnavailable(sagaId: String, reservation: Reservation) {
        outbox.send(
            Topology.REPLIES_EXCHANGE, Topology.ORCHESTRATOR, sagaId, MessageTypes.STOCK_UNAVAILABLE,
            CommandReply(MessageTypes.RESERVE_STOCK, success = false, reason = reservation.reason),
        )
    }
}
