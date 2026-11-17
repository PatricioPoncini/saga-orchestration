package dev.saga.order.adapter.out.persistence

import dev.saga.order.application.port.out.OrderRepository
import dev.saga.order.domain.Order
import dev.saga.order.domain.OrderStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class JdbcOrderRepository(private val jdbc: JdbcClient) : OrderRepository {
    private val mapper = RowMapper { rs, _ ->
        Order(
            id = rs.getString("id"),
            sagaId = rs.getString("saga_id"),
            customerId = rs.getString("customer_id"),
            productId = rs.getString("product_id"),
            quantity = rs.getInt("quantity"),
            amount = rs.getBigDecimal("amount"),
            status = OrderStatus.valueOf(rs.getString("status")),
            rejectionReason = rs.getString("rejection_reason"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }

    override fun findById(id: String): Order? =
        jdbc.sql("select * from orders where id = :id for update")
            .param("id", id)
            .query(mapper)
            .optional()
            .orElse(null)

    override fun save(order: Order) {
        jdbc.sql(
            """
            insert into orders (id, saga_id, customer_id, product_id, quantity, amount, status, rejection_reason, created_at, updated_at)
            values (:id, :sagaId, :customerId, :productId, :quantity, :amount, :status, :rejectionReason, :createdAt, :updatedAt)
            on conflict (id) do update set
                status = excluded.status,
                rejection_reason = excluded.rejection_reason,
                updated_at = excluded.updated_at
            """.trimIndent(),
        )
            .param("id", order.id)
            .param("sagaId", order.sagaId)
            .param("customerId", order.customerId)
            .param("productId", order.productId)
            .param("quantity", order.quantity)
            .param("amount", order.amount)
            .param("status", order.status.name)
            .param("rejectionReason", order.rejectionReason)
            .param("createdAt", Timestamp.from(order.createdAt))
            .param("updatedAt", Timestamp.from(order.updatedAt))
            .update()
    }

    override fun findAll(): List<Order> =
        jdbc.sql("select * from orders order by created_at desc limit 50").query(mapper).list()
}
