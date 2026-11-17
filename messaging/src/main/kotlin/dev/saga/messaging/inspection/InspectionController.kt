package dev.saga.messaging.inspection

import dev.saga.messaging.reset.DemoReset
import org.springframework.http.HttpStatus
import java.time.Instant
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/inspect")
class InspectionController(
    private val inspector: DatabaseInspector,
    private val broker: BrokerInspector,
    private val reset: DemoReset,
) {
    data class ResetStatus(val enabled: Boolean, val intervalSeconds: Long, val nextResetAt: Instant?, val lastResetAt: Instant?)

    @GetMapping("/database")
    fun database() = inspector.database()

    @GetMapping("/queues")
    fun queues() = broker.queues()

    @GetMapping("/reset")
    fun resetStatus() = ResetStatus(reset.enabled, reset.interval.seconds, reset.nextResetAt(), reset.lastResetAt)

    @GetMapping("/tables/{table}")
    fun rows(
        @PathVariable table: String,
        @RequestParam(defaultValue = "25") limit: Int,
        @RequestParam(required = false) sagaId: String?,
        @RequestParam(required = false) orderId: String?,
    ) = inspector.rows(table, limit, sagaId, orderId)

    @GetMapping("/outbox")
    fun outbox(@RequestParam sagaId: String) = inspector.outbox(sagaId)

    @GetMapping("/inbox")
    fun inbox(@RequestParam(required = false) ids: List<String>?) = inspector.inbox(ids.orEmpty())

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun unknownTable(e: IllegalArgumentException) = mapOf("error" to e.message)
}
