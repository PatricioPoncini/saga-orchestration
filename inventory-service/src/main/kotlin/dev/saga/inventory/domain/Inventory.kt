package dev.saga.inventory.domain

import java.math.BigDecimal
import java.time.Instant

data class Product(
    val id: String,
    val name: String,
    val price: BigDecimal,
    val available: Int,
    val reserved: Int,
) {
    fun canReserve(quantity: Int): Boolean = quantity in 1..available

    fun reserve(quantity: Int): Product {
        require(canReserve(quantity)) { "Stock insuficiente: disponible $available, pedido $quantity" }
        return copy(available = available - quantity, reserved = reserved + quantity)
    }

    fun restock(quantity: Int): Product {
        require(quantity > 0) { "La cantidad debe ser positiva" }
        return copy(available = available + quantity)
    }
}

enum class ReservationStatus { RESERVED, REJECTED }

data class Reservation(
    val orderId: String,
    val productId: String,
    val quantity: Int,
    val status: ReservationStatus,
    val reason: String?,
    val createdAt: Instant,
)
