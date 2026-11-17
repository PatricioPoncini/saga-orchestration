package dev.saga.messaging

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import java.math.BigDecimal
import java.time.Instant

data class Envelope(
    val messageId: String,
    val sagaId: String,
    val type: String,
    val source: String,
    val payload: JsonNode,
    val occurredAt: Instant,
    val headers: Map<String, String> = emptyMap(),
)

inline fun <reified T> Envelope.payloadAs(mapper: ObjectMapper): T = mapper.treeToValue(payload)

object MessageTypes {
    const val START_CREATE_ORDER_SAGA = "StartCreateOrderSaga"

    const val AUTHORIZE_PAYMENT = "AuthorizePayment"
    const val REFUND_PAYMENT = "RefundPayment"
    const val RESERVE_STOCK = "ReserveStock"
    const val APPROVE_ORDER = "ApproveOrder"
    const val REJECT_ORDER = "RejectOrder"

    const val PAYMENT_AUTHORIZED = "PaymentAuthorized"
    const val PAYMENT_REJECTED = "PaymentRejected"
    const val PAYMENT_REFUNDED = "PaymentRefunded"
    const val STOCK_RESERVED = "StockReserved"
    const val STOCK_UNAVAILABLE = "StockUnavailable"
    const val ORDER_APPROVED = "OrderApproved"
    const val ORDER_REJECTED = "OrderRejected"
}

data class StartCreateOrderSaga(
    val orderId: String,
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val amount: BigDecimal,
)

data class AuthorizePayment(val orderId: String, val customerId: String, val amount: BigDecimal)
data class RefundPayment(val orderId: String)
data class ReserveStock(val orderId: String, val productId: String, val quantity: Int)
data class ApproveOrder(val orderId: String)
data class RejectOrder(val orderId: String, val reason: String)

data class CommandReply(
    val commandType: String,
    val success: Boolean,
    val reason: String? = null,
    val data: Map<String, String> = emptyMap(),
)

data class TraceEvent(
    val sagaId: String?,
    val service: String,
    val kind: String,
    val messageType: String?,
    val messageId: String?,
    val description: String,
    val occurredAt: Instant,
)

object TraceKinds {
    const val MESSAGE_PUBLISHED = "MESSAGE_PUBLISHED"
    const val MESSAGE_RECEIVED = "MESSAGE_RECEIVED"
    const val DUPLICATE_IGNORED = "DUPLICATE_IGNORED"
    const val LOCAL_TX_COMMITTED = "LOCAL_TX_COMMITTED"
    const val CHAOS = "CHAOS"
}
