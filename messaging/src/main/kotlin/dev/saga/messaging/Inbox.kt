package dev.saga.messaging

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.support.TransactionTemplate

class Inbox(private val jdbc: JdbcClient) {
    fun markProcessed(messageId: String, messageType: String): Boolean =
        jdbc.sql(
            """
            insert into processed_messages (message_id, message_type, processed_at)
            values (:messageId, :messageType, now())
            on conflict do nothing
            """.trimIndent(),
        )
            .param("messageId", messageId)
            .param("messageType", messageType)
            .update() == 1
}

class IdempotentMessageHandler(
    private val inbox: Inbox,
    private val tx: TransactionTemplate,
    private val trace: TraceEmitter,
) {
    fun handle(envelope: Envelope, traceReceipt: Boolean = true, block: () -> String?) =
        MessageContext.with(envelope.headers) { process(envelope, traceReceipt, block) }

    private fun process(envelope: Envelope, traceReceipt: Boolean, block: () -> String?) {
        if (traceReceipt) {
            trace.emit(
                sagaId = envelope.sagaId,
                kind = TraceKinds.MESSAGE_RECEIVED,
                messageType = envelope.type,
                messageId = envelope.messageId,
                description = "Received ${envelope.type} from ${envelope.source} (msg ${envelope.messageId.take(8)})",
            )
        }

        var description: String? = null
        val firstTime = tx.execute {
            if (!inbox.markProcessed(envelope.messageId, envelope.type)) {
                false
            } else {
                description = block()
                true
            }
        } ?: false

        if (!firstTime) {
            trace.emit(
                sagaId = envelope.sagaId,
                kind = TraceKinds.DUPLICATE_IGNORED,
                messageType = envelope.type,
                messageId = envelope.messageId,
                description = "Message ${envelope.messageId.take(8)} (${envelope.type}) already processed: ignored (Inbox)",
            )
        } else {
            description?.let {
                trace.emit(
                    sagaId = envelope.sagaId,
                    kind = TraceKinds.LOCAL_TX_COMMITTED,
                    messageType = envelope.type,
                    messageId = envelope.messageId,
                    description = it,
                )
            }
        }
    }
}
