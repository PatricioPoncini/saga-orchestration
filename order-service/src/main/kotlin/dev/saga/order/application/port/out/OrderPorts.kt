package dev.saga.order.application.port.out

import dev.saga.order.domain.Order

interface OrderRepository {
    fun findById(id: String): Order?
    fun save(order: Order)
    fun findAll(): List<Order>
}

interface SagaStarterPort {
    fun startCreateOrderSaga(order: Order)
}

interface OrderReplyPort {
    fun orderApproved(sagaId: String, order: Order)
    fun orderRejected(sagaId: String, order: Order)
}
