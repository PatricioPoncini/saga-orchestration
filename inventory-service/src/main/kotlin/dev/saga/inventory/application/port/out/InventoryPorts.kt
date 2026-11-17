package dev.saga.inventory.application.port.out

import dev.saga.inventory.domain.Product
import dev.saga.inventory.domain.Reservation

interface ProductRepository {
    fun findByIdForUpdate(id: String): Product?
    fun save(product: Product)
    fun findAll(): List<Product>
}

interface ReservationRepository {
    fun findByOrderId(orderId: String): Reservation?
    fun save(reservation: Reservation)
    fun findAll(): List<Reservation>
}

interface InventoryReplyPort {
    fun stockReserved(sagaId: String, reservation: Reservation)
    fun stockUnavailable(sagaId: String, reservation: Reservation)
}
