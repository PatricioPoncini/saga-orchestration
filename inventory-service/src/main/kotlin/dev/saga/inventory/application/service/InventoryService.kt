package dev.saga.inventory.application.service

import dev.saga.inventory.application.port.`in`.InventoryQuery
import dev.saga.inventory.application.port.`in`.ReserveStockCommand
import dev.saga.inventory.application.port.`in`.ReserveStockUseCase
import dev.saga.inventory.application.port.`in`.RestockUseCase
import dev.saga.inventory.application.port.out.InventoryReplyPort
import dev.saga.inventory.application.port.out.ProductRepository
import dev.saga.inventory.application.port.out.ReservationRepository
import dev.saga.inventory.domain.Product
import dev.saga.inventory.domain.Reservation
import dev.saga.inventory.domain.ReservationStatus
import java.time.Clock

class InventoryService(
    private val products: ProductRepository,
    private val reservations: ReservationRepository,
    private val replies: InventoryReplyPort,
    private val clock: Clock,
) : ReserveStockUseCase, RestockUseCase, InventoryQuery {
    override fun reserve(command: ReserveStockCommand): Reservation {
        val reservation = reservations.findByOrderId(command.orderId) ?: newReservation(command)

        when (reservation.status) {
            ReservationStatus.RESERVED -> replies.stockReserved(command.sagaId, reservation)
            ReservationStatus.REJECTED -> replies.stockUnavailable(command.sagaId, reservation)
        }
        return reservation
    }

    private fun newReservation(command: ReserveStockCommand): Reservation {
        val product = products.findByIdForUpdate(command.productId)
        val reservation = when {
            product == null -> rejected(command, "Producto ${command.productId} inexistente")
            !product.canReserve(command.quantity) ->
                rejected(command, "Stock insuficiente de '${product.name}': disponible ${product.available}, pedido ${command.quantity}")
            else -> {
                products.save(product.reserve(command.quantity))
                Reservation(command.orderId, command.productId, command.quantity, ReservationStatus.RESERVED, null, clock.instant())
            }
        }
        reservations.save(reservation)
        return reservation
    }

    private fun rejected(command: ReserveStockCommand, reason: String) =
        Reservation(command.orderId, command.productId, command.quantity, ReservationStatus.REJECTED, reason, clock.instant())

    override fun restock(productId: String, quantity: Int): Product {
        val product = products.findByIdForUpdate(productId) ?: throw NoSuchElementException("Producto $productId inexistente")
        return product.restock(quantity).also(products::save)
    }

    override fun products(): List<Product> = products.findAll()

    override fun reservations(): List<Reservation> = reservations.findAll()
}
