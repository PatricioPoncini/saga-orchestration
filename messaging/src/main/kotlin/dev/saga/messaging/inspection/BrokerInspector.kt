package dev.saga.messaging.inspection

import dev.saga.messaging.Topology
import org.springframework.amqp.core.AmqpAdmin
import java.time.Duration

class BrokerInspector(private val admin: AmqpAdmin) {
    data class QueueStatus(val name: String, val messages: Int, val consumers: Int)

    private val queues = listOf(
        Topology.ORCHESTRATOR_QUEUE,
        Topology.PAYMENT_QUEUE,
        Topology.INVENTORY_QUEUE,
        Topology.ORDER_QUEUE,
        Topology.REPLIES_QUEUE,
        Topology.TRACE_QUEUE,
        Topology.INVENTORY_RETRY_QUEUE,
        Topology.DEAD_LETTER_QUEUE,
    )

    private val cache = TtlCache<List<QueueStatus>>(Duration.ofSeconds(1))

    fun queues(): List<QueueStatus> = cache.get { load() }

    private fun load(): List<QueueStatus> = queues.map { name ->
        val info = try {
            admin.getQueueInfo(name)
        } catch (e: Exception) {
            null
        }
        QueueStatus(name, info?.messageCount ?: 0, info?.consumerCount ?: 0)
    }
}
