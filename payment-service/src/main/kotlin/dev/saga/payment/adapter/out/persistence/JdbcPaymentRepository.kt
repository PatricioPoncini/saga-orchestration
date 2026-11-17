package dev.saga.payment.adapter.out.persistence

import dev.saga.payment.application.port.out.PaymentRepository
import dev.saga.payment.domain.Payment
import dev.saga.payment.domain.PaymentStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class JdbcPaymentRepository(private val jdbc: JdbcClient) : PaymentRepository {
    private val mapper = RowMapper { rs, _ ->
        Payment(
            id = rs.getString("id"),
            orderId = rs.getString("order_id"),
            customerId = rs.getString("customer_id"),
            amount = rs.getBigDecimal("amount"),
            status = PaymentStatus.valueOf(rs.getString("status")),
            reason = rs.getString("reason"),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }

    override fun findByOrderId(orderId: String): Payment? =
        jdbc.sql("select * from payments where order_id = :orderId")
            .param("orderId", orderId)
            .query(mapper)
            .optional()
            .orElse(null)

    override fun save(payment: Payment) {
        jdbc.sql(
            """
            insert into payments (id, order_id, customer_id, amount, status, reason, updated_at)
            values (:id, :orderId, :customerId, :amount, :status, :reason, :updatedAt)
            on conflict (id) do update set status = excluded.status, reason = excluded.reason, updated_at = excluded.updated_at
            """.trimIndent(),
        )
            .param("id", payment.id)
            .param("orderId", payment.orderId)
            .param("customerId", payment.customerId)
            .param("amount", payment.amount)
            .param("status", payment.status.name)
            .param("reason", payment.reason)
            .param("updatedAt", Timestamp.from(payment.updatedAt))
            .update()
    }

    override fun findAll(): List<Payment> =
        jdbc.sql("select * from payments order by updated_at desc limit 50").query(mapper).list()
}
