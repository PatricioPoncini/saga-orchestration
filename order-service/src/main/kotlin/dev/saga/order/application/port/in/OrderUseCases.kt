package dev.saga.order.application.port.`in`

import dev.saga.order.domain.Order
import java.math.BigDecimal

data class CreateOrderCommand(
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val amount: BigDecimal,
)

interface CreateOrderUseCase {
    fun create(command: CreateOrderCommand): Order
}

interface ApproveOrderUseCase {
    fun approve(sagaId: String, orderId: String): Order
}

interface RejectOrderUseCase {
    fun reject(sagaId: String, orderId: String, reason: String): Order
}

interface OrderQuery {
    fun orders(): List<Order>
}
