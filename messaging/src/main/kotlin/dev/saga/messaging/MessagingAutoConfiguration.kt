package dev.saga.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import dev.saga.messaging.inspection.BrokerInspector
import dev.saga.messaging.inspection.DatabaseInspector
import dev.saga.messaging.inspection.InspectionController
import dev.saga.messaging.reset.DemoReset
import dev.saga.messaging.reset.DemoResetScheduler
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.io.ResourceLoader
import java.time.Duration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.support.TransactionTemplate

@AutoConfiguration
@EnableScheduling
class MessagingAutoConfiguration {
    @Bean
    fun sagaTopology(): Declarables {
        val commands = DirectExchange(Topology.COMMANDS_EXCHANGE)
        val replies = DirectExchange(Topology.REPLIES_EXCHANGE)
        val trace = FanoutExchange(Topology.TRACE_EXCHANGE)
        val dlx = DirectExchange(Topology.DEAD_LETTER_EXCHANGE)

        fun queue(name: String): Queue = QueueBuilder.durable(name)
            .deadLetterExchange(Topology.DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(Topology.DEAD)
            .build()

        val orderQueue = queue(Topology.ORDER_QUEUE)
        val paymentQueue = queue(Topology.PAYMENT_QUEUE)
        val inventoryQueue = queue(Topology.INVENTORY_QUEUE)
        val orchestratorQueue = queue(Topology.ORCHESTRATOR_QUEUE)
        val repliesQueue = queue(Topology.REPLIES_QUEUE)
        val traceQueue = QueueBuilder.durable(Topology.TRACE_QUEUE).build()
        val deadLetters = QueueBuilder.durable(Topology.DEAD_LETTER_QUEUE).build()
        val retry = DirectExchange(Topology.RETRY_EXCHANGE)
        val inventoryRetryQueue = QueueBuilder.durable(Topology.INVENTORY_RETRY_QUEUE)
            .ttl(Topology.RETRY_DELAY_MS)
            .deadLetterExchange(Topology.COMMANDS_EXCHANGE)
            .deadLetterRoutingKey(Topology.INVENTORY)
            .build()

        val bindings: List<Binding> = listOf(
            BindingBuilder.bind(orderQueue).to(commands).with(Topology.ORDER),
            BindingBuilder.bind(paymentQueue).to(commands).with(Topology.PAYMENT),
            BindingBuilder.bind(inventoryQueue).to(commands).with(Topology.INVENTORY),
            BindingBuilder.bind(orchestratorQueue).to(commands).with(Topology.ORCHESTRATOR),
            BindingBuilder.bind(repliesQueue).to(replies).with(Topology.ORCHESTRATOR),
            BindingBuilder.bind(traceQueue).to(trace),
            BindingBuilder.bind(deadLetters).to(dlx).with(Topology.DEAD),
            BindingBuilder.bind(inventoryRetryQueue).to(retry).with(Topology.INVENTORY),
        )

        return Declarables(
            listOf(
                commands, replies, trace, dlx, retry,
                orderQueue, paymentQueue, inventoryQueue, orchestratorQueue, repliesQueue, traceQueue, deadLetters,
                inventoryRetryQueue,
            ) + bindings,
        )
    }

    @Bean
    fun traceEmitter(
        rabbit: RabbitTemplate,
        mapper: ObjectMapper,
        @Value("\${spring.application.name}") serviceName: String,
    ) = TraceEmitter(rabbit, mapper, serviceName)

    @Bean
    fun outbox(
        jdbc: JdbcClient,
        mapper: ObjectMapper,
        @Value("\${spring.application.name}") serviceName: String,
    ) = Outbox(jdbc, mapper, serviceName)

    @Bean
    fun outboxRelay(jdbc: JdbcClient, rabbit: RabbitTemplate, tx: TransactionTemplate, trace: TraceEmitter) =
        OutboxRelay(jdbc, rabbit, tx, trace)

    @Bean
    fun inbox(jdbc: JdbcClient) = Inbox(jdbc)

    @Bean
    fun idempotentMessageHandler(inbox: Inbox, tx: TransactionTemplate, trace: TraceEmitter) =
        IdempotentMessageHandler(inbox, tx, trace)

    @Bean
    fun demoReset(
        jdbc: JdbcClient,
        tx: TransactionTemplate,
        amqpAdmin: AmqpAdmin,
        resources: ResourceLoader,
        @Value("\${demo.reset.interval:15m}") interval: Duration,
        @Value("\${demo.reset.purge-queues:}") queues: List<String>,
    ) = DemoReset(jdbc, tx, amqpAdmin, resources, interval, queues.filter { it.isNotBlank() })

    @Bean
    fun demoResetScheduler(reset: DemoReset) = DemoResetScheduler(reset)

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication
    class InspectionConfiguration {
        @Bean
        fun databaseInspector(jdbc: JdbcClient, @Value("\${spring.application.name}") serviceName: String) =
            DatabaseInspector(jdbc, serviceName)

        @Bean
        fun brokerInspector(admin: AmqpAdmin) = BrokerInspector(admin)

        @Bean
        fun inspectionController(inspector: DatabaseInspector, broker: BrokerInspector, reset: DemoReset) =
            InspectionController(inspector, broker, reset)
    }
}
