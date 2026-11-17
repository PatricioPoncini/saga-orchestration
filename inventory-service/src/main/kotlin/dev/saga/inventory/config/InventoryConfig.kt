package dev.saga.inventory.config

import dev.saga.inventory.application.port.out.InventoryReplyPort
import dev.saga.inventory.application.port.out.ProductRepository
import dev.saga.inventory.application.port.out.ReservationRepository
import dev.saga.inventory.application.service.InventoryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class InventoryConfig {
    @Bean
    fun inventoryService(
        products: ProductRepository,
        reservations: ReservationRepository,
        replies: InventoryReplyPort,
    ) = InventoryService(products, reservations, replies, Clock.systemUTC())
}
