package dev.saga.orchestrator.domain

import dev.saga.orchestrator.domain.order.CreateOrderSaga
import dev.saga.orchestrator.domain.order.CreateOrderSagaData
import dev.saga.orchestrator.domain.saga.SagaDecision
import dev.saga.orchestrator.domain.saga.SagaEngine
import dev.saga.orchestrator.domain.saga.SagaInstance
import dev.saga.orchestrator.domain.saga.SagaReply
import dev.saga.orchestrator.domain.saga.SagaStatus
import dev.saga.orchestrator.domain.saga.StepState
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SagaEngineTest {
    private val engine = SagaEngine(CreateOrderSaga.definition, Duration.ofSeconds(10), maxAttempts = 3)
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val data = CreateOrderSagaData("order-1", "customer-1", "p-1", 1, BigDecimal("120"))

    private fun ok(type: String, data: Map<String, String> = emptyMap()) = SagaReply(type, true, null, data)
    private fun fail(type: String, reason: String) = SagaReply(type, false, reason)

    private fun SagaDecision<CreateOrderSagaData>.advance() = assertIs<SagaDecision.Advance<CreateOrderSagaData>>(this)

    @Test
    fun `happy path executes every step in order`() {
        var d = engine.start("s", data, t0)
        assertEquals("AuthorizePayment", d.command?.type)
        assertEquals(StepState.SUCCEEDED, d.instance.stepStates[0])

        d = engine.onReply(d.instance, ok("AuthorizePayment", mapOf("paymentId" to "pay-1")), t0).advance()
        assertEquals("ReserveStock", d.command?.type)
        assertEquals("pay-1", d.instance.data.paymentId)

        d = engine.onReply(d.instance, ok("ReserveStock"), t0).advance()
        assertEquals("ApproveOrder", d.command?.type)

        d = engine.onReply(d.instance, ok("ApproveOrder"), t0).advance()
        assertNull(d.command)
        assertEquals(SagaStatus.COMPLETED, d.instance.status)
        assertEquals(List(4) { StepState.SUCCEEDED }, d.instance.stepStates)
    }

    @Test
    fun `payment rejected compensates only the order`() {
        val started = engine.start("s", data, t0)
        val d = engine.onReply(started.instance, fail("AuthorizePayment", "sin fondos"), t0).advance()

        assertEquals(SagaStatus.COMPENSATING, d.instance.status)
        assertEquals("RejectOrder", d.command?.type)
        assertEquals("sin fondos", d.command?.payload?.get("reason"))

        val done = engine.onReply(d.instance, ok("RejectOrder"), t0).advance()
        assertEquals(SagaStatus.COMPENSATED, done.instance.status)
        assertEquals(
            listOf(StepState.COMPENSATED, StepState.FAILED, StepState.SKIPPED, StepState.SKIPPED),
            done.instance.stepStates,
        )
    }

    @Test
    fun `pivot failure compensates previous steps in reverse order`() {
        var d = engine.start("s", data, t0)
        d = engine.onReply(d.instance, ok("AuthorizePayment"), t0).advance()
        d = engine.onReply(d.instance, fail("ReserveStock", "sin stock"), t0).advance()
        assertEquals("RefundPayment", d.command?.type)

        d = engine.onReply(d.instance, ok("RefundPayment"), t0).advance()
        assertEquals("RejectOrder", d.command?.type)

        d = engine.onReply(d.instance, ok("RejectOrder"), t0).advance()
        assertEquals(SagaStatus.COMPENSATED, d.instance.status)
    }

    @Test
    fun `late or duplicated replies are ignored`() {
        var d = engine.start("s", data, t0)
        d = engine.onReply(d.instance, ok("AuthorizePayment"), t0).advance()
        val ignored = engine.onReply(d.instance, ok("AuthorizePayment"), t0)
        assertIs<SagaDecision.Ignore<CreateOrderSagaData>>(ignored)
    }

    @Test
    fun `timeout resends the command and finally fails the saga`() {
        var instance: SagaInstance<CreateOrderSagaData> = engine.start("s", data, t0).instance
        var now = t0

        assertIs<SagaDecision.Ignore<CreateOrderSagaData>>(engine.onTimeout(instance, now.plusSeconds(5)))

        repeat(2) {
            now = now.plusSeconds(11)
            val d = engine.onTimeout(instance, now).advance()
            assertEquals("AuthorizePayment", d.command?.type)
            instance = d.instance
        }
        assertEquals(3, instance.awaiting?.attempts)

        val failed = engine.onTimeout(instance, now.plusSeconds(11)).advance()
        assertEquals(SagaStatus.FAILED, failed.instance.status)
        assertNull(failed.command)
    }
}
