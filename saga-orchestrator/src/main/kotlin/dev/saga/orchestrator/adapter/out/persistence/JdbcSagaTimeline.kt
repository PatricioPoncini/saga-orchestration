package dev.saga.orchestrator.adapter.out.persistence

import dev.saga.orchestrator.adapter.`in`.web.SseHub
import dev.saga.orchestrator.application.port.out.SagaTimeline
import dev.saga.orchestrator.application.port.out.TimelineEntry
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Timestamp

@Repository
class JdbcSagaTimeline(
    private val jdbc: JdbcClient,
    private val hub: SseHub,
) : SagaTimeline {
    private val mapper = RowMapper { rs, _ ->
        TimelineEntry(
            id = rs.getLong("id"),
            sagaId = rs.getString("saga_id"),
            occurredAt = rs.getTimestamp("occurred_at").toInstant(),
            kind = rs.getString("kind"),
            actor = rs.getString("actor"),
            target = rs.getString("target"),
            messageType = rs.getString("message_type"),
            description = rs.getString("description"),
        )
    }

    override fun record(entry: TimelineEntry) {
        val keys = GeneratedKeyHolder()
        jdbc.sql(
            """
            insert into saga_timeline (saga_id, occurred_at, kind, actor, target, message_type, description)
            values (:sagaId, :occurredAt, :kind, :actor, :target, :messageType, :description)
            """.trimIndent(),
        )
            .param("sagaId", entry.sagaId)
            .param("occurredAt", Timestamp.from(entry.occurredAt))
            .param("kind", entry.kind)
            .param("actor", entry.actor)
            .param("target", entry.target)
            .param("messageType", entry.messageType)
            .param("description", entry.description)
            .update(keys, "id")
        val saved = entry.copy(id = keys.key?.toLong())

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = hub.broadcast(saved.sagaId, saved)
            })
        } else {
            hub.broadcast(saved.sagaId, saved)
        }
    }

    override fun findBySaga(sagaId: String): List<TimelineEntry> =
        jdbc.sql("select * from saga_timeline where saga_id = :sagaId order by occurred_at, id")
            .param("sagaId", sagaId)
            .query(mapper)
            .list()
}
