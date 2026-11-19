package dev.saga.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

class Outbox(
    private val jdbc: JdbcClient,
    private val mapper: ObjectMapper,
    private val serviceName: String,
) {
    fun send(
        exchange: String,
        routingKey: String,
        sagaId: String,
        type: String,
        payload: Any,
        messageId: String = UUID.randomUUID().toString(),
        headers: Map<String, String> = MessageContext.headers(),
        delay: Duration = Duration.ZERO,
    ): Envelope {
        val envelope = Envelope(
            messageId = messageId,
            sagaId = sagaId,
            type = type,
            source = serviceName,
            payload = mapper.valueToTree(payload),
            occurredAt = Instant.now(),
            headers = headers,
        )
        jdbc.sql(
            """
            insert into outbox (message_id, saga_id, exchange, routing_key, type, body, created_at, available_at)
            values (:messageId, :sagaId, :exchange, :routingKey, :type, :body, now(), now() + make_interval(secs => :delaySeconds))
            """.trimIndent(),
        )
            .param("messageId", envelope.messageId)
            .param("sagaId", sagaId)
            .param("exchange", exchange)
            .param("routingKey", routingKey)
            .param("type", type)
            .param("body", mapper.writeValueAsString(envelope))
            .param("delaySeconds", delay.toMillis() / 1000.0)
            .update()
        return envelope
    }
}

class OutboxRelay(
    private val jdbc: JdbcClient,
    private val rabbit: RabbitTemplate,
    private val tx: TransactionTemplate,
    private val trace: TraceEmitter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private data class PendingMessage(
        val id: Long,
        val messageId: String,
        val sagaId: String,
        val exchange: String,
        val routingKey: String,
        val type: String,
        val body: String,
    )

    @Scheduled(fixedDelay = 200)
    fun publishPending() {
        try {
            tx.executeWithoutResult {
                val pending = jdbc.sql(
                    """
                    select id, message_id, saga_id, exchange, routing_key, type, body
                    from outbox
                    where published_at is null and available_at <= now()
                    order by id
                    limit 100
                    for update skip locked
                    """.trimIndent(),
                ).query { rs, _ ->
                    PendingMessage(
                        id = rs.getLong("id"),
                        messageId = rs.getString("message_id"),
                        sagaId = rs.getString("saga_id"),
                        exchange = rs.getString("exchange"),
                        routingKey = rs.getString("routing_key"),
                        type = rs.getString("type"),
                        body = rs.getString("body"),
                    )
                }.list()

                pending.forEach { message ->
                    rabbit.convertAndSend(message.exchange, message.routingKey, message.body)
                    jdbc.sql("update outbox set published_at = now() where id = :id")
                        .param("id", message.id)
                        .update()
                    trace.emit(
                        sagaId = message.sagaId,
                        kind = TraceKinds.MESSAGE_PUBLISHED,
                        messageType = message.type,
                        messageId = message.messageId,
                        description = "Outbox relay published ${message.type} to ${message.exchange} → ${message.routingKey}",
                    )
                }
            }
        } catch (e: Exception) {
            log.warn("Outbox relay failed, it will retry: {}", e.message)
        }
    }
}
