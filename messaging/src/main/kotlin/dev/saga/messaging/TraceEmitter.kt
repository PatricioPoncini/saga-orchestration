package dev.saga.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant

class TraceEmitter(
    private val rabbit: RabbitTemplate,
    private val mapper: ObjectMapper,
    private val serviceName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun emit(
        sagaId: String?,
        kind: String,
        messageType: String? = null,
        messageId: String? = null,
        description: String,
    ) {
        log.info("[trace] saga={} {} {}", sagaId?.take(8), kind, description)
        try {
            val event = TraceEvent(sagaId, serviceName, kind, messageType, messageId, description, Instant.now())
            rabbit.convertAndSend(Topology.TRACE_EXCHANGE, "", mapper.writeValueAsString(event))
        } catch (e: Exception) {
            log.warn("Could not publish trace: {}", e.message)
        }
    }
}
