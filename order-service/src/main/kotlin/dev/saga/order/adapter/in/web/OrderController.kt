package dev.saga.order.adapter.`in`.web

import dev.saga.messaging.DemoFaults
import dev.saga.messaging.MessageContext
import dev.saga.messaging.MessageTypes
import dev.saga.messaging.TraceEmitter
import dev.saga.messaging.TraceKinds
import dev.saga.order.application.port.`in`.CreateOrderCommand
import dev.saga.order.application.port.`in`.CreateOrderUseCase
import dev.saga.order.application.port.`in`.OrderQuery
import dev.saga.order.domain.Order
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

data class CreateOrderRequest(
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val amount: BigDecimal,
    val faults: DemoFaults = DemoFaults(),
)

@RestController
class OrderController(
    private val createOrder: CreateOrderUseCase,
    private val query: OrderQuery,
    private val tx: TransactionTemplate,
    private val trace: TraceEmitter,
) {
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun create(@RequestBody request: CreateOrderRequest): Order {
        val order = MessageContext.with(request.faults.toHeaders()) {
            tx.execute {
                createOrder.create(CreateOrderCommand(request.customerId, request.productId, request.quantity, request.amount))
            }!!
        }
        trace.emit(
            sagaId = order.sagaId,
            kind = TraceKinds.LOCAL_TX_COMMITTED,
            messageType = MessageTypes.START_CREATE_ORDER_SAGA,
            description = "Order ${order.id.take(8)} created as PENDING + StartCreateOrderSaga in the outbox (same transaction)",
        )
        return order
    }

    @GetMapping("/orders")
    fun list(): List<Order> = query.orders()

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalid(e: IllegalArgumentException) = mapOf("error" to e.message)
}
