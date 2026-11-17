package dev.saga.orchestrator.application.service

import dev.saga.orchestrator.application.port.`in`.HandleReplyUseCase
import dev.saga.orchestrator.application.port.`in`.HandleTimeoutsUseCase
import dev.saga.orchestrator.application.port.`in`.QuerySagasUseCase
import dev.saga.orchestrator.application.port.`in`.RecordTraceUseCase
import dev.saga.orchestrator.application.port.`in`.StartCreateOrderSagaCommand
import dev.saga.orchestrator.application.port.`in`.StartSagaUseCase
import dev.saga.orchestrator.application.port.out.CommandDispatcher
import dev.saga.orchestrator.application.port.out.SagaRepository
import dev.saga.orchestrator.application.port.out.SagaTimeline
import dev.saga.orchestrator.application.port.out.TimelineEntry
import dev.saga.orchestrator.application.port.out.TimelineKinds
import dev.saga.orchestrator.domain.order.CreateOrderSagaData
import dev.saga.orchestrator.domain.saga.SagaDecision
import dev.saga.orchestrator.domain.saga.SagaEngine
import dev.saga.orchestrator.domain.saga.SagaInstance
import dev.saga.orchestrator.domain.saga.SagaReply
import java.time.Clock

class SagaOrchestratorService(
    private val engine: SagaEngine<CreateOrderSagaData>,
    private val sagas: SagaRepository,
    private val dispatcher: CommandDispatcher,
    private val timeline: SagaTimeline,
    private val clock: Clock,
) : StartSagaUseCase, HandleReplyUseCase, HandleTimeoutsUseCase, RecordTraceUseCase, QuerySagasUseCase {
    override fun start(command: StartCreateOrderSagaCommand) {
        if (sagas.findById(command.sagaId) != null) return
        val decision = engine.start(command.sagaId, command.data, clock.instant(), command.context)
        sagas.insert(decision.instance)
        apply(decision)
    }

    override fun handleReply(sagaId: String, replyType: String, from: String, reply: SagaReply) {
        val instance = sagas.findByIdForUpdate(sagaId) ?: return
        val outcome = if (reply.success) "OK" else "FALLÓ: ${reply.reason}"
        record(sagaId, TimelineKinds.REPLY_RECEIVED, actor = from, target = ORCHESTRATOR, type = replyType,
            description = "$replyType ($outcome)")

        when (val decision = engine.onReply(instance, reply, clock.instant())) {
            is SagaDecision.Ignore -> record(sagaId, TimelineKinds.REPLY_IGNORED, ORCHESTRATOR, null, replyType, decision.reason)
            is SagaDecision.Advance -> {
                sagas.update(decision.instance)
                apply(decision)
            }
        }
    }

    override fun sagasAwaitingReply(): List<String> = sagas.findIdsAwaitingReply()

    override fun checkTimeout(sagaId: String) {
        val instance = sagas.findByIdForUpdate(sagaId) ?: return
        val decision = engine.onTimeout(instance, clock.instant())
        if (decision is SagaDecision.Advance) {
            sagas.update(decision.instance)
            apply(decision)
        }
    }

    override fun record(entry: TimelineEntry) = timeline.record(entry)

    override fun definition() = engine.definition
    override fun list(): List<SagaInstance<CreateOrderSagaData>> = sagas.findAll()
    override fun listByIds(ids: List<String>) = sagas.findByIds(ids.distinct().take(MAX_IDS))
    override fun get(sagaId: String) = sagas.findById(sagaId)
    override fun timeline(sagaId: String) = timeline.findBySaga(sagaId)

    private fun apply(decision: SagaDecision.Advance<CreateOrderSagaData>) {
        val saga = decision.instance
        decision.notes.forEach { record(saga.id, TimelineKinds.SAGA_NOTE, ORCHESTRATOR, null, null, it) }

        decision.command?.let { command ->
            dispatcher.dispatch(saga.id, command, saga.context)
            val label = if (saga.awaiting?.compensating == true) "compensación" else "comando"
            record(saga.id, TimelineKinds.COMMAND_SENT, ORCHESTRATOR, command.participant, command.type,
                "Envía $label ${command.type} a ${command.participant} (intento ${saga.awaiting?.attempts ?: 1})")
        }

        if (saga.status.terminal) {
            record(saga.id, TimelineKinds.SAGA_FINISHED, ORCHESTRATOR, null, null,
                "Saga finalizada: ${saga.status}" + (saga.failureReason?.let { " — $it" } ?: ""))
        }
    }

    private fun record(sagaId: String, kind: String, actor: String, target: String?, type: String?, description: String) {
        timeline.record(TimelineEntry(sagaId, clock.instant(), kind, actor, target, type, description))
    }

    companion object {
        const val ORCHESTRATOR = "orchestrator"
        const val MAX_IDS = 50
    }
}
