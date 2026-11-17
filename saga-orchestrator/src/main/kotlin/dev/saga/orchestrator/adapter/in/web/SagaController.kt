package dev.saga.orchestrator.adapter.`in`.web

import dev.saga.orchestrator.application.port.`in`.QuerySagasUseCase
import dev.saga.orchestrator.application.port.out.TimelineEntry
import dev.saga.orchestrator.domain.order.CreateOrderSagaData
import dev.saga.orchestrator.domain.saga.AwaitingReply
import dev.saga.orchestrator.domain.saga.SagaInstance
import dev.saga.orchestrator.domain.saga.StepState
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant

data class StepView(
    val name: String,
    val participant: String,
    val kind: String,
    val description: String,
    val compensation: String?,
    val state: StepState,
)

data class SagaView(
    val id: String,
    val type: String,
    val status: String,
    val data: CreateOrderSagaData,
    val steps: List<StepView>,
    val awaiting: AwaitingReply?,
    val failureReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class SagaDetail(val saga: SagaView, val timeline: List<TimelineEntry>)

@RestController
class SagaController(
    private val sagas: QuerySagasUseCase,
    private val hub: SseHub,
) {
    @GetMapping("/sagas/definition")
    fun definition(): List<StepView> =
        sagas.definition().steps.map {
            StepView(it.name, it.participant, it.kind.name, it.description, it.compensationName, StepState.PENDING)
        }

    @GetMapping("/sagas")
    fun list(@RequestParam(required = false) ids: List<String>?): List<SagaView> =
        (if (ids == null) sagas.list() else sagas.listByIds(ids)).map(::toView)

    @GetMapping("/sagas/{id}")
    fun get(@PathVariable id: String): ResponseEntity<SagaDetail> {
        val saga = sagas.get(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(SagaDetail(toView(saga), sagas.timeline(id)))
    }

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@RequestParam(required = false) sagaId: String?): SseEmitter = hub.subscribe(sagaId)

    private fun toView(saga: SagaInstance<CreateOrderSagaData>): SagaView {
        val steps = sagas.definition().steps.mapIndexed { i, step ->
            StepView(step.name, step.participant, step.kind.name, step.description, step.compensationName, saga.stepStates[i])
        }
        return SagaView(
            saga.id, saga.type, saga.status.name, saga.data, steps, saga.awaiting, saga.failureReason,
            saga.createdAt, saga.updatedAt,
        )
    }
}
