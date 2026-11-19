package dev.saga.orchestrator.domain.order

import dev.saga.orchestrator.domain.saga.SagaCommand
import dev.saga.orchestrator.domain.saga.SagaDefinition
import dev.saga.orchestrator.domain.saga.SagaStep
import dev.saga.orchestrator.domain.saga.StepKind
import java.math.BigDecimal

data class CreateOrderSagaData(
    val orderId: String,
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val amount: BigDecimal,
    val paymentId: String? = null,
)

object CreateOrderSaga {
    const val ORDER = "order"
    const val PAYMENT = "payment"
    const val INVENTORY = "inventory"

    val definition = SagaDefinition<CreateOrderSagaData>(
        name = "CreateOrderSaga",
        steps = listOf(
            SagaStep(
                name = "CREATE_ORDER",
                participant = ORDER,
                kind = StepKind.COMPENSATABLE,
                description = "Create the order in PENDING state (local transaction of order-service)",
                action = null,
                compensation = { data, reason ->
                    SagaCommand(ORDER, "RejectOrder", mapOf("orderId" to data.orderId, "reason" to reason))
                },
                compensationName = "RejectOrder",
            ),
            SagaStep(
                name = "AUTHORIZE_PAYMENT",
                participant = PAYMENT,
                kind = StepKind.COMPENSATABLE,
                description = "Authorize the charge to the customer",
                action = { data ->
                    SagaCommand(
                        PAYMENT, "AuthorizePayment",
                        mapOf("orderId" to data.orderId, "customerId" to data.customerId, "amount" to data.amount),
                    )
                },
                onSuccess = { data, reply -> data.copy(paymentId = reply["paymentId"]) },
                compensation = { data, _ -> SagaCommand(PAYMENT, "RefundPayment", mapOf("orderId" to data.orderId)) },
                compensationName = "RefundPayment",
            ),
            SagaStep(
                name = "RESERVE_STOCK",
                participant = INVENTORY,
                kind = StepKind.PIVOT,
                description = "Reserve stock. Point of no return: if it succeeds, the saga will complete",
                action = { data ->
                    SagaCommand(
                        INVENTORY, "ReserveStock",
                        mapOf("orderId" to data.orderId, "productId" to data.productId, "quantity" to data.quantity),
                    )
                },
            ),
            SagaStep(
                name = "APPROVE_ORDER",
                participant = ORDER,
                kind = StepKind.RETRIABLE,
                description = "Approve the order. It cannot fail for business reasons: it is only retried",
                action = { data -> SagaCommand(ORDER, "ApproveOrder", mapOf("orderId" to data.orderId)) },
            ),
        ),
    )
}
