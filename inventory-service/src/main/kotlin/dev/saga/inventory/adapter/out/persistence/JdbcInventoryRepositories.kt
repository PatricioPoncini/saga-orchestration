package dev.saga.inventory.adapter.out.persistence

import dev.saga.inventory.application.port.out.ProductRepository
import dev.saga.inventory.application.port.out.ReservationRepository
import dev.saga.inventory.domain.Product
import dev.saga.inventory.domain.Reservation
import dev.saga.inventory.domain.ReservationStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class JdbcProductRepository(private val jdbc: JdbcClient) : ProductRepository {
    private val mapper = RowMapper { rs, _ ->
        Product(
            id = rs.getString("id"),
            name = rs.getString("name"),
            price = rs.getBigDecimal("price"),
            available = rs.getInt("available"),
            reserved = rs.getInt("reserved"),
        )
    }

    override fun findByIdForUpdate(id: String): Product? =
        jdbc.sql("select * from products where id = :id for update")
            .param("id", id)
            .query(mapper)
            .optional()
            .orElse(null)

    override fun save(product: Product) {
        jdbc.sql("update products set available = :available, reserved = :reserved where id = :id")
            .param("available", product.available)
            .param("reserved", product.reserved)
            .param("id", product.id)
            .update()
    }

    override fun findAll(): List<Product> = jdbc.sql("select * from products order by id").query(mapper).list()
}

@Repository
class JdbcReservationRepository(private val jdbc: JdbcClient) : ReservationRepository {
    private val mapper = RowMapper { rs, _ ->
        Reservation(
            orderId = rs.getString("order_id"),
            productId = rs.getString("product_id"),
            quantity = rs.getInt("quantity"),
            status = ReservationStatus.valueOf(rs.getString("status")),
            reason = rs.getString("reason"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }

    override fun findByOrderId(orderId: String): Reservation? =
        jdbc.sql("select * from reservations where order_id = :orderId")
            .param("orderId", orderId)
            .query(mapper)
            .optional()
            .orElse(null)

    override fun save(reservation: Reservation) {
        jdbc.sql(
            """
            insert into reservations (order_id, product_id, quantity, status, reason, created_at)
            values (:orderId, :productId, :quantity, :status, :reason, :createdAt)
            """.trimIndent(),
        )
            .param("orderId", reservation.orderId)
            .param("productId", reservation.productId)
            .param("quantity", reservation.quantity)
            .param("status", reservation.status.name)
            .param("reason", reservation.reason)
            .param("createdAt", Timestamp.from(reservation.createdAt))
            .update()
    }

    override fun findAll(): List<Reservation> =
        jdbc.sql("select * from reservations order by created_at desc limit 50").query(mapper).list()
}
