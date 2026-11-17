package dev.saga.orchestrator.config

import dev.saga.orchestrator.application.port.out.CommandDispatcher
import dev.saga.orchestrator.application.port.out.SagaRepository
import dev.saga.orchestrator.application.port.out.SagaTimeline
import dev.saga.orchestrator.application.service.SagaOrchestratorService
import dev.saga.orchestrator.domain.order.CreateOrderSaga
import dev.saga.orchestrator.domain.saga.SagaEngine
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

@Configuration
class OrchestratorConfig {
    @Bean
    fun createOrderSagaEngine(
        @Value("\${saga.reply-timeout:10s}") replyTimeout: Duration,
        @Value("\${saga.max-attempts:5}") maxAttempts: Int,
    ) = SagaEngine(CreateOrderSaga.definition, replyTimeout, maxAttempts)

    @Bean
    fun sagaOrchestratorService(
        engine: SagaEngine<dev.saga.orchestrator.domain.order.CreateOrderSagaData>,
        sagas: SagaRepository,
        dispatcher: CommandDispatcher,
        timeline: SagaTimeline,
    ) = SagaOrchestratorService(engine, sagas, dispatcher, timeline, Clock.systemUTC())
}
