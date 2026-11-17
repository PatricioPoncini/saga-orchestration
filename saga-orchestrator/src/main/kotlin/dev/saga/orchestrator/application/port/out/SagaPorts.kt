package dev.saga.orchestrator.application.port.out

import dev.saga.orchestrator.domain.order.CreateOrderSagaData
import dev.saga.orchestrator.domain.saga.SagaCommand
import dev.saga.orchestrator.domain.saga.SagaInstance
import java.time.Instant

interface SagaRepository {
    fun insert(instance: SagaInstance<CreateOrderSagaData>)
    fun update(instance: SagaInstance<CreateOrderSagaData>)
    fun findById(id: String): SagaInstance<CreateOrderSagaData>?

    fun findByIdForUpdate(id: String): SagaInstance<CreateOrderSagaData>?
    fun findAll(): List<SagaInstance<CreateOrderSagaData>>
    fun findByIds(ids: List<String>): List<SagaInstance<CreateOrderSagaData>>
    fun findIdsAwaitingReply(): List<String>
}

interface CommandDispatcher {
    fun dispatch(sagaId: String, command: SagaCommand, context: Map<String, String>)
}

data class TimelineEntry(
    val sagaId: String,
    val occurredAt: Instant,
    val kind: String,
    val actor: String,
    val target: String?,
    val messageType: String?,
    val description: String,
    val id: Long? = null,
)

object TimelineKinds {
    const val SAGA_NOTE = "SAGA_NOTE"
    const val COMMAND_SENT = "COMMAND_SENT"
    const val REPLY_RECEIVED = "REPLY_RECEIVED"
    const val REPLY_IGNORED = "REPLY_IGNORED"
    const val SAGA_FINISHED = "SAGA_FINISHED"
}

interface SagaTimeline {
    fun record(entry: TimelineEntry)
    fun findBySaga(sagaId: String): List<TimelineEntry>
}
