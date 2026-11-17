package dev.saga.payment.config

import dev.saga.payment.application.port.out.FailureInjectionPort
import dev.saga.payment.application.port.out.PaymentReplyPort
import dev.saga.payment.application.port.out.PaymentRepository
import dev.saga.payment.application.service.PaymentService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class PaymentConfig {
    @Bean
    fun paymentService(
        repository: PaymentRepository,
        replies: PaymentReplyPort,
        failures: FailureInjectionPort,
    ) = PaymentService(repository, replies, failures, Clock.systemUTC())
}
