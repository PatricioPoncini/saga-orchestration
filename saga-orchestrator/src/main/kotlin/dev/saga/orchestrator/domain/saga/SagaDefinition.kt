package dev.saga.orchestrator.domain.saga

enum class StepKind { COMPENSATABLE, PIVOT, RETRIABLE }

data class SagaCommand(
    val participant: String,
    val type: String,
    val payload: Map<String, Any?>,
)

class SagaStep<D>(
    val name: String,
    val participant: String,
    val kind: StepKind,
    val description: String,
    val action: ((D) -> SagaCommand)? = null,
    val onSuccess: (D, Map<String, String>) -> D = { data, _ -> data },
    val compensation: ((D, String) -> SagaCommand)? = null,
    val compensationName: String? = null,
)

class SagaDefinition<D>(
    val name: String,
    val steps: List<SagaStep<D>>,
)
