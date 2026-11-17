package dev.saga.inventory.adapter.`in`.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.saga.inventory.adapter.`in`.demo.SimulatedOutages
import dev.saga.inventory.application.port.`in`.ReserveStockCommand
import dev.saga.inventory.application.port.`in`.ReserveStockUseCase
import dev.saga.messaging.DemoFaults
import dev.saga.messaging.Envelope
import dev.saga.messaging.IdempotentMessageHandler
import dev.saga.messaging.MessageTypes
import dev.saga.messaging.ReserveStock
import dev.saga.messaging.Topology
import dev.saga.messaging.TraceEmitter
import dev.saga.messaging.TraceKinds
import dev.saga.messaging.payloadAs
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

@Component
class InventoryCommandListener(
    private val mapper: ObjectMapper,
    private val handler: IdempotentMessageHandler,
    private val reserveStock: ReserveStockUseCase,
    private val outages: SimulatedOutages,
    private val rabbit: RabbitTemplate,
    private val trace: TraceEmitter,
) {
    @RabbitListener(queues = [Topology.INVENTORY_QUEUE])
    fun onCommand(body: String) {
        val envelope: Envelope = mapper.readValue(body)

        if (outages.isDown(envelope.sagaId, DemoFaults.from(envelope.headers))) {
            sendToRetryQueue(envelope)
            return
        }

        handler.handle(envelope) {
            when (envelope.type) {
                MessageTypes.RESERVE_STOCK -> {
                    val cmd = envelope.payloadAs<ReserveStock>(mapper)
                    val r = reserveStock.reserve(ReserveStockCommand(envelope.sagaId, cmd.orderId, cmd.productId, cmd.quantity))
                    "Reserva de ${r.quantity} x ${r.productId} → ${r.status}" + (r.reason?.let { " ($it)" } ?: "")
                }
                else -> error("Comando desconocido: ${envelope.type}")
            }
        }
    }

    private fun sendToRetryQueue(envelope: Envelope) {
        val retries = envelope.headers[RETRIES]?.toIntOrNull() ?: 0
        if (retries == 0) {
            trace.emit(
                envelope.sagaId, TraceKinds.CHAOS, envelope.type, envelope.messageId,
                "inventory está caído para esta saga: el mensaje vuelve a esperar en ${Topology.INVENTORY_RETRY_QUEUE} y se reintenta cada ${Topology.RETRY_DELAY_MS / 1000} s",
            )
        }
        val retried = envelope.copy(headers = envelope.headers + (RETRIES to (retries + 1).toString()))
        rabbit.convertAndSend(Topology.RETRY_EXCHANGE, Topology.INVENTORY, mapper.writeValueAsString(retried))
    }

    companion object {
        const val RETRIES = "demo.retries"
    }
}
