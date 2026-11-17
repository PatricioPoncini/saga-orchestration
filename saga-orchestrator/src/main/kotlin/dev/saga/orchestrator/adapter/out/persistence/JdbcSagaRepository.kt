package dev.saga.orchestrator.adapter.out.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.saga.orchestrator.application.port.out.SagaRepository
import dev.saga.orchestrator.domain.order.CreateOrderSagaData
import dev.saga.orchestrator.domain.saga.AwaitingReply
import dev.saga.orchestrator.domain.saga.SagaInstance
import dev.saga.orchestrator.domain.saga.SagaStatus
import dev.saga.orchestrator.domain.saga.StepState
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp

@Repository
class JdbcSagaRepository(
    private val jdbc: JdbcClient,
    private val json: ObjectMapper,
) : SagaRepository {
    private val mapper = RowMapper { rs, _ ->
        SagaInstance(
            id = rs.getString("id"),
            type = rs.getString("type"),
            data = json.readValue<CreateOrderSagaData>(rs.getString("data")),
            status = SagaStatus.valueOf(rs.getString("status")),
            stepStates = rs.getString("step_states").split(",").map(StepState::valueOf),
            awaiting = rs.getString("awaiting")?.let { json.readValue<AwaitingReply>(it) },
            failureReason = rs.getString("failure_reason"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            context = json.readValue<Map<String, String>>(rs.getString("context")),
        )
    }

    override fun insert(instance: SagaInstance<CreateOrderSagaData>) {
        jdbc.sql(
            """
            insert into saga_instance (id, type, status, data, step_states, awaiting, awaiting_since, failure_reason, created_at, updated_at, context)
            values (:id, :type, :status, :data, :stepStates, :awaiting, :awaitingSince, :failureReason, :createdAt, :updatedAt, :context)
            """.trimIndent(),
        ).bindSaga(instance).update()
    }

    override fun update(instance: SagaInstance<CreateOrderSagaData>) {
        jdbc.sql(
            """
            update saga_instance set
                status = :status, data = :data, step_states = :stepStates, awaiting = :awaiting,
                awaiting_since = :awaitingSince, failure_reason = :failureReason, updated_at = :updatedAt
            where id = :id
            """.trimIndent(),
        ).bindSaga(instance).update()
    }

    private fun JdbcClient.StatementSpec.bindSaga(i: SagaInstance<CreateOrderSagaData>) = this
        .param("id", i.id)
        .param("type", i.type)
        .param("status", i.status.name)
        .param("data", json.writeValueAsString(i.data))
        .param("context", json.writeValueAsString(i.context))
        .param("stepStates", i.stepStates.joinToString(","))
        .param("awaiting", i.awaiting?.let(json::writeValueAsString))
        .param("awaitingSince", i.awaiting?.sentAt?.let(Timestamp::from))
        .param("failureReason", i.failureReason)
        .param("createdAt", Timestamp.from(i.createdAt))
        .param("updatedAt", Timestamp.from(i.updatedAt))

    override fun findById(id: String) =
        jdbc.sql("select * from saga_instance where id = :id").param("id", id).query(mapper).optional().orElse(null)

    override fun findByIdForUpdate(id: String) =
        jdbc.sql("select * from saga_instance where id = :id for update").param("id", id).query(mapper).optional().orElse(null)

    override fun findAll(): List<SagaInstance<CreateOrderSagaData>> =
        jdbc.sql("select * from saga_instance order by created_at desc limit 50").query(mapper).list()

    override fun findByIds(ids: List<String>): List<SagaInstance<CreateOrderSagaData>> =
        if (ids.isEmpty()) emptyList()
        else jdbc.sql("select * from saga_instance where id in (:ids) order by created_at desc").param("ids", ids).query(mapper).list()

    override fun findIdsAwaitingReply(): List<String> =
        jdbc.sql("select id from saga_instance where awaiting is not null and status in ('RUNNING', 'COMPENSATING')")
            .query(String::class.java)
            .list()
}
