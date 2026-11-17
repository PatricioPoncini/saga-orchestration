package dev.saga.orchestrator.application.port.`in`

import dev.saga.orchestrator.application.port.out.TimelineEntry
import dev.saga.orchestrator.domain.order.CreateOrderSagaData
import dev.saga.orchestrator.domain.saga.SagaDefinition
import dev.saga.orchestrator.domain.saga.SagaInstance
import dev.saga.orchestrator.domain.saga.SagaReply

data class StartCreateOrderSagaCommand(
    val sagaId: String,
    val data: CreateOrderSagaData,
    val context: Map<String, String> = emptyMap(),
)

interface StartSagaUseCase {
    fun start(command: StartCreateOrderSagaCommand)
}

interface HandleReplyUseCase {
    fun handleReply(sagaId: String, replyType: String, from: String, reply: SagaReply)
}

interface HandleTimeoutsUseCase {
    fun sagasAwaitingReply(): List<String>
    fun checkTimeout(sagaId: String)
}

interface RecordTraceUseCase {
    fun record(entry: TimelineEntry)
}

interface QuerySagasUseCase {
    fun definition(): SagaDefinition<CreateOrderSagaData>
    fun list(): List<SagaInstance<CreateOrderSagaData>>
    fun listByIds(ids: List<String>): List<SagaInstance<CreateOrderSagaData>>
    fun get(sagaId: String): SagaInstance<CreateOrderSagaData>?
    fun timeline(sagaId: String): List<TimelineEntry>
}
