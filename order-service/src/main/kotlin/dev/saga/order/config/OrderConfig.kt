package dev.saga.order.config

import dev.saga.order.application.port.out.OrderReplyPort
import dev.saga.order.application.port.out.OrderRepository
import dev.saga.order.application.port.out.SagaStarterPort
import dev.saga.order.application.service.OrderService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class OrderConfig {
    @Bean
    fun orderService(
        orders: OrderRepository,
        sagaStarter: SagaStarterPort,
        replies: OrderReplyPort,
    ) = OrderService(orders, sagaStarter, replies, Clock.systemUTC())
}
