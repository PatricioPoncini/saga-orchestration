package dev.saga.inventory.adapter.`in`.web

import dev.saga.inventory.adapter.`in`.demo.SimulatedOutages
import dev.saga.inventory.application.port.`in`.InventoryQuery
import dev.saga.inventory.application.port.`in`.RestockUseCase
import dev.saga.inventory.domain.Product
import dev.saga.inventory.domain.Reservation
import dev.saga.messaging.TraceEmitter
import dev.saga.messaging.TraceKinds
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class RestockRequest(val quantity: Int)

@RestController
class InventoryController(
    private val query: InventoryQuery,
    private val restock: RestockUseCase,
    private val tx: TransactionTemplate,
    private val outages: SimulatedOutages,
    private val trace: TraceEmitter,
) {
    @GetMapping("/products")
    fun products(): List<Product> = query.products()

    @GetMapping("/reservations")
    fun reservations(): List<Reservation> = query.reservations()

    @PostMapping("/products/{id}/restock")
    fun restock(@PathVariable id: String, @RequestBody request: RestockRequest): Product =
        tx.execute { restock.restock(id, request.quantity) }!!

    @GetMapping("/outages/{sagaId}")
    fun outage(@PathVariable sagaId: String) = outages.status(sagaId)

    @PostMapping("/outages/{sagaId}/recover")
    fun recover(@PathVariable sagaId: String): SimulatedOutages.Status {
        if (outages.status(sagaId).down) {
            outages.recover(sagaId)
            trace.emit(sagaId, TraceKinds.CHAOS, description = "inventory is available again for this saga: it processes the messages that were waiting")
        }
        return outages.status(sagaId)
    }
}
