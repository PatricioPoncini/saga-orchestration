package dev.saga.orchestrator.adapter.`in`.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.saga.messaging.CommandReply
import dev.saga.messaging.Envelope
import dev.saga.messaging.IdempotentMessageHandler
import dev.saga.messaging.MessageTypes
import dev.saga.messaging.StartCreateOrderSaga
import dev.saga.messaging.Topology
import dev.saga.messaging.TraceEvent
import dev.saga.messaging.payloadAs
import dev.saga.orchestrator.application.port.`in`.HandleReplyUseCase
import dev.saga.orchestrator.application.port.`in`.RecordTraceUseCase
import dev.saga.orchestrator.application.port.`in`.StartCreateOrderSagaCommand
import dev.saga.orchestrator.application.port.`in`.StartSagaUseCase
import dev.saga.orchestrator.application.port.out.TimelineEntry
import dev.saga.orchestrator.domain.order.CreateOrderSagaData
import dev.saga.orchestrator.domain.saga.SagaReply
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class OrchestratorListeners(
    private val mapper: ObjectMapper,
    private val handler: IdempotentMessageHandler,
    private val startSaga: StartSagaUseCase,
    private val handleReply: HandleReplyUseCase,
    private val recordTrace: RecordTraceUseCase,
) {
    @RabbitListener(queues = [Topology.ORCHESTRATOR_QUEUE])
    fun onStart(body: String) {
        val envelope: Envelope = mapper.readValue(body)
        require(envelope.type == MessageTypes.START_CREATE_ORDER_SAGA) { "Unknown message: ${envelope.type}" }
        handler.handle(envelope, traceReceipt = false) {
            val p = envelope.payloadAs<StartCreateOrderSaga>(mapper)
            startSaga.start(
                StartCreateOrderSagaCommand(
                    envelope.sagaId,
                    CreateOrderSagaData(p.orderId, p.customerId, p.productId, p.quantity, p.amount),
                    envelope.headers,
                ),
            )
            null
        }
    }

    @RabbitListener(queues = [Topology.REPLIES_QUEUE])
    fun onReply(body: String) {
        val envelope: Envelope = mapper.readValue(body)
        handler.handle(envelope, traceReceipt = false) {
            val reply = envelope.payloadAs<CommandReply>(mapper)
            handleReply.handleReply(
                sagaId = envelope.sagaId,
                replyType = envelope.type,
                from = participantName(envelope.source),
                reply = SagaReply(reply.commandType, reply.success, reply.reason, reply.data),
            )
            null
        }
    }

    @RabbitListener(queues = [Topology.TRACE_QUEUE])
    fun onTrace(body: String) {
        val event: TraceEvent = mapper.readValue(body)
        val sagaId = event.sagaId ?: return
        recordTrace.record(
            TimelineEntry(
                sagaId = sagaId,
                occurredAt = event.occurredAt,
                kind = event.kind,
                actor = participantName(event.service),
                target = null,
                messageType = event.messageType,
                description = event.description,
            ),
        )
    }

    private fun participantName(service: String) = service.removeSuffix("-service").replace("saga-", "")
}
