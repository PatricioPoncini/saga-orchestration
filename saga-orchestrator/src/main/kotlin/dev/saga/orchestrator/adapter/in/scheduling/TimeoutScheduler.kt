package dev.saga.orchestrator.adapter.`in`.scheduling

import dev.saga.orchestrator.application.port.`in`.HandleTimeoutsUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class TimeoutScheduler(
    private val timeouts: HandleTimeoutsUseCase,
    private val tx: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    fun checkTimeouts() {
        timeouts.sagasAwaitingReply().forEach { sagaId ->
            try {
                tx.executeWithoutResult { timeouts.checkTimeout(sagaId) }
            } catch (e: Exception) {
                log.warn("Error revisando timeout de la saga {}: {}", sagaId, e.message)
            }
        }
    }
}
