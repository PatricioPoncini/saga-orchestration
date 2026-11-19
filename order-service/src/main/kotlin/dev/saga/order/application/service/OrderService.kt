package dev.saga.order.application.service

import dev.saga.order.application.port.`in`.ApproveOrderUseCase
import dev.saga.order.application.port.`in`.CreateOrderCommand
import dev.saga.order.application.port.`in`.CreateOrderUseCase
import dev.saga.order.application.port.`in`.OrderQuery
import dev.saga.order.application.port.`in`.RejectOrderUseCase
import dev.saga.order.application.port.out.OrderReplyPort
import dev.saga.order.application.port.out.OrderRepository
import dev.saga.order.application.port.out.SagaStarterPort
import dev.saga.order.domain.Order
import java.time.Clock
import java.util.UUID

class OrderService(
    private val orders: OrderRepository,
    private val sagaStarter: SagaStarterPort,
    private val replies: OrderReplyPort,
    private val clock: Clock,
) : CreateOrderUseCase, ApproveOrderUseCase, RejectOrderUseCase, OrderQuery {
    override fun create(command: CreateOrderCommand): Order {
        val order = Order.create(
            id = UUID.randomUUID().toString(),
            sagaId = UUID.randomUUID().toString(),
            customerId = command.customerId,
            productId = command.productId,
            quantity = command.quantity,
            amount = command.amount,
            now = clock.instant(),
        )
        orders.save(order)
        sagaStarter.startCreateOrderSaga(order)
        return order
    }

    override fun approve(sagaId: String, orderId: String): Order {
        val order = load(orderId).approve(clock.instant())
        orders.save(order)
        replies.orderApproved(sagaId, order)
        return order
    }

    override fun reject(sagaId: String, orderId: String, reason: String): Order {
        val order = load(orderId).reject(reason, clock.instant())
        orders.save(order)
        replies.orderRejected(sagaId, order)
        return order
    }

    override fun orders(): List<Order> = orders.findAll()

    private fun load(orderId: String) = orders.findById(orderId)
        ?: throw NoSuchElementException("Order $orderId not found")
}
