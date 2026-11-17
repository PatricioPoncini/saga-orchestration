package dev.saga.order.domain

import java.math.BigDecimal
import java.time.Instant

enum class OrderStatus { PENDING, APPROVED, REJECTED }

data class Order(
    val id: String,
    val sagaId: String,
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val amount: BigDecimal,
    val status: OrderStatus,
    val rejectionReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun approve(now: Instant): Order = when (status) {
        OrderStatus.PENDING -> copy(status = OrderStatus.APPROVED, updatedAt = now)
        OrderStatus.APPROVED -> this
        OrderStatus.REJECTED -> throw IllegalStateException("No se puede aprobar la orden $id: ya fue rechazada")
    }

    fun reject(reason: String, now: Instant): Order = when (status) {
        OrderStatus.PENDING -> copy(status = OrderStatus.REJECTED, rejectionReason = reason, updatedAt = now)
        OrderStatus.REJECTED -> this
        OrderStatus.APPROVED -> throw IllegalStateException("No se puede rechazar la orden $id: ya fue aprobada")
    }

    companion object {
        const val MAX_QUANTITY = 100
        val MAX_AMOUNT: BigDecimal = BigDecimal("100000")
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

        fun create(
            id: String,
            sagaId: String,
            customerId: String,
            productId: String,
            quantity: Int,
            amount: BigDecimal,
            now: Instant,
        ): Order {
            require(quantity in 1..MAX_QUANTITY) { "La cantidad debe estar entre 1 y $MAX_QUANTITY" }
            require(amount > BigDecimal.ZERO && amount <= MAX_AMOUNT) { "El monto debe ser positivo y no superar $MAX_AMOUNT" }
            require(customerId.isNotBlank() && customerId.length <= 64) { "customerId inválido" }
            require(ID_PATTERN.matches(productId)) { "productId inválido" }
            return Order(id, sagaId, customerId, productId, quantity, amount, OrderStatus.PENDING, null, now, now)
        }
    }
}
