package dev.saga.messaging.reset

import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.core.io.ResourceLoader
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

class DemoReset(
    private val jdbc: JdbcClient,
    private val tx: TransactionTemplate,
    private val amqpAdmin: AmqpAdmin,
    private val resources: ResourceLoader,
    val interval: Duration,
    private val queues: List<String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    var lastResetAt: Instant? = null
        private set

    val enabled: Boolean get() = !interval.isZero && !interval.isNegative

    fun nextResetAt(now: Instant = Instant.now()): Instant? = if (enabled) nextBoundary(now, interval) else null

    fun reset() {
        purgeQueues()
        tx.executeWithoutResult {
            val tables = jdbc.sql(
                """
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE' and table_name <> 'flyway_schema_history'
                """.trimIndent(),
            ).query(String::class.java).list()

            if (tables.isNotEmpty()) {
                jdbc.sql("truncate table ${tables.joinToString { "\"$it\"" }} restart identity").update()
            }
            seedSql()?.let { jdbc.sql(it).update() }
            log.info("Demo reiniciada: {} tablas vaciadas", tables.size)
        }
        purgeQueues()
        lastResetAt = Instant.now()
    }

    private fun purgeQueues() = queues.forEach { queue ->
        try {
            amqpAdmin.purgeQueue(queue, false)
        } catch (e: Exception) {
            log.warn("No se pudo purgar la cola {}: {}", queue, e.message)
        }
    }

    private fun seedSql(): String? {
        val resource = resources.getResource("classpath:db/reset/seed.sql")
        return if (resource.exists()) resource.inputStream.use { String(it.readAllBytes()) } else null
    }

    companion object {
        fun nextBoundary(now: Instant, interval: Duration): Instant {
            val step = interval.toMillis()
            val from = now.toEpochMilli() + 1_000
            return Instant.ofEpochMilli((from / step + 1) * step)
        }
    }
}

class DemoResetScheduler(private val reset: DemoReset) : SchedulingConfigurer {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun configureTasks(registrar: ScheduledTaskRegistrar) {
        if (!reset.enabled) {
            log.info("Reinicio automático de la demo desactivado (DEMO_RESET_INTERVAL=0)")
            return
        }
        log.info("Reinicio automático de la demo cada {} (próximo: {})", reset.interval, reset.nextResetAt())
        registrar.addTriggerTask({ reset.reset() }) { reset.nextResetAt(Instant.now()) }
    }
}
