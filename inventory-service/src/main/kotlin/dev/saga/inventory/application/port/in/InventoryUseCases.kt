package dev.saga.inventory.application.port.`in`

import dev.saga.inventory.domain.Product
import dev.saga.inventory.domain.Reservation

data class ReserveStockCommand(val sagaId: String, val orderId: String, val productId: String, val quantity: Int)

interface ReserveStockUseCase {
    fun reserve(command: ReserveStockCommand): Reservation
}

interface RestockUseCase {
    fun restock(productId: String, quantity: Int): Product
}

interface InventoryQuery {
    fun products(): List<Product>
    fun reservations(): List<Reservation>
}
