package dev.saga.orchestrator.domain.saga

import java.time.Instant

enum class SagaStatus {
    RUNNING, COMPENSATING, COMPLETED, COMPENSATED, FAILED;

    val terminal: Boolean get() = this == COMPLETED || this == COMPENSATED || this == FAILED
}

enum class StepState { PENDING, RUNNING, SUCCEEDED, FAILED, COMPENSATING, COMPENSATED, SKIPPED }

data class AwaitingReply(
    val stepIndex: Int,
    val commandType: String,
    val participant: String,
    val compensating: Boolean,
    val sentAt: Instant,
    val attempts: Int,
)

data class SagaInstance<D>(
    val id: String,
    val type: String,
    val data: D,
    val status: SagaStatus,
    val stepStates: List<StepState>,
    val awaiting: AwaitingReply?,
    val failureReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val context: Map<String, String> = emptyMap(),
)

data class SagaReply(
    val commandType: String,
    val success: Boolean,
    val reason: String?,
    val data: Map<String, String> = emptyMap(),
)

sealed interface SagaDecision<D> {
    data class Advance<D>(
        val instance: SagaInstance<D>,
        val command: SagaCommand?,
        val notes: List<String>,
    ) : SagaDecision<D>

    data class Ignore<D>(val reason: String) : SagaDecision<D>
}
