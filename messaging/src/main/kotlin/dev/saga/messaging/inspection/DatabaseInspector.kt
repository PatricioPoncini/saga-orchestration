package dev.saga.messaging.inspection

import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

class DatabaseInspector(
    private val jdbc: JdbcClient,
    private val serviceName: String,
) {
    data class Column(val name: String, val type: String, val nullable: Boolean, val primaryKey: Boolean)

    data class TableInfo(
        val name: String,
        val category: String,
        val columns: List<Column>,
        val rowCount: Long,
        val pending: Long? = null,
    )

    data class DatabaseInfo(val service: String, val database: String, val tables: List<TableInfo>)

    data class TableRows(
        val table: String,
        val columns: List<Column>,
        val rows: List<Map<String, Any?>>,
        val total: Long,
        val filteredBySaga: Boolean,
    )

    data class OutboxMessage(
        val id: Long,
        val messageId: String,
        val sagaId: String,
        val type: String,
        val exchange: String,
        val routingKey: String,
        val createdAt: Instant,
        val publishedAt: Instant?,
    )

    data class ProcessedMessage(val messageId: String, val type: String, val processedAt: Instant)

    private val databaseCache = TtlCache<DatabaseInfo>(Duration.ofSeconds(3))
    private val columnsCache = TtlCache<Map<String, List<Column>>>(Duration.ofSeconds(30))

    fun database(): DatabaseInfo = databaseCache.get { loadDatabase() }

    private fun loadDatabase(): DatabaseInfo {
        val database = jdbc.sql("select current_database()").query(String::class.java).single()
        val tables = describeColumns().map { (table, columns) ->
            TableInfo(
                name = table,
                category = categoryOf(table),
                columns = columns,
                rowCount = count(table),
                pending = if (table == "outbox") {
                    jdbc.sql("select count(*) from outbox where published_at is null").query(Long::class.javaObjectType).single()
                } else {
                    null
                },
            )
        }
        return DatabaseInfo(serviceName, database, tables)
    }

    fun rows(table: String, limit: Int, sagaId: String?, orderId: String?): TableRows {
        val columns = describeColumns()[table]
            ?: throw IllegalArgumentException("Unknown table: $table")
        val names = columns.map { it.name }.toSet()
        val filter = when {
            sagaId != null && "saga_id" in names -> "saga_id" to sagaId
            orderId != null && "order_id" in names -> "order_id" to orderId
            else -> null
        }
        val orderBy = listOf("created_at", "occurred_at", "processed_at", "updated_at", "id").firstOrNull { it in names }

        val sql = buildString {
            append("select * from \"").append(table).append('"')
            if (filter != null) append(" where \"").append(filter.first).append("\" = :value")
            if (orderBy != null) append(" order by \"").append(orderBy).append("\" desc")
            append(" limit :limit")
        }
        var statement = jdbc.sql(sql).param("limit", limit.coerceIn(1, 200))
        if (filter != null) statement = statement.param("value", filter.second)
        val rows = statement.query().listOfRows().map { row -> row.mapValues { (_, value) -> normalize(value) } }

        return TableRows(table, columns, rows, count(table), filter != null)
    }

    fun outbox(sagaId: String): List<OutboxMessage> =
        jdbc.sql(
            """
            select id, message_id, saga_id, type, exchange, routing_key, created_at, published_at
            from outbox where saga_id = :sagaId order by id
            """.trimIndent(),
        )
            .param("sagaId", sagaId)
            .query { rs, _ ->
                OutboxMessage(
                    id = rs.getLong("id"),
                    messageId = rs.getString("message_id"),
                    sagaId = rs.getString("saga_id"),
                    type = rs.getString("type"),
                    exchange = rs.getString("exchange"),
                    routingKey = rs.getString("routing_key"),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                    publishedAt = rs.getTimestamp("published_at")?.toInstant(),
                )
            }
            .list()

    fun inbox(messageIds: List<String>): List<ProcessedMessage> {
        if (messageIds.isEmpty()) return emptyList()
        return jdbc.sql("select message_id, message_type, processed_at from processed_messages where message_id in (:ids)")
            .param("ids", messageIds.take(500))
            .query { rs, _ ->
                ProcessedMessage(rs.getString("message_id"), rs.getString("message_type"), rs.getTimestamp("processed_at").toInstant())
            }
            .list()
    }

    private fun describeColumns(): Map<String, List<Column>> = columnsCache.get { loadColumns() }

    private fun loadColumns(): Map<String, List<Column>> {
        val primaryKeys = jdbc.sql(
            """
            select k.table_name, k.column_name
            from information_schema.table_constraints tc
            join information_schema.key_column_usage k
              on tc.constraint_name = k.constraint_name and tc.table_schema = k.table_schema
            where tc.table_schema = 'public' and tc.constraint_type = 'PRIMARY KEY'
            """.trimIndent(),
        ).query { rs, _ -> rs.getString(1) to rs.getString(2) }.list().toSet()

        return jdbc.sql(
            """
            select table_name, column_name, data_type, is_nullable
            from information_schema.columns
            where table_schema = 'public' and table_name <> 'flyway_schema_history'
            order by table_name, ordinal_position
            """.trimIndent(),
        ).query { rs, _ ->
            val table = rs.getString("table_name")
            val column = rs.getString("column_name")
            table to Column(column, rs.getString("data_type"), rs.getString("is_nullable") == "YES", (table to column) in primaryKeys)
        }.list().groupBy({ it.first }, { it.second })
    }

    private fun count(table: String): Long =
        jdbc.sql("select count(*) from \"$table\"").query(Long::class.javaObjectType).single()

    private fun categoryOf(table: String) = when (table) {
        "outbox", "processed_messages" -> "messaging"
        "saga_instance", "saga_timeline" -> "saga"
        else -> "domain"
    }

    private fun normalize(value: Any?): Any? = when (value) {
        null -> null
        is Timestamp -> value.toInstant().toString()
        is OffsetDateTime -> value.toInstant().toString()
        is BigDecimal, is Number, is Boolean -> value
        else -> value.toString().let { if (it.length > 400) it.take(400) + "…" else it }
    }
}
