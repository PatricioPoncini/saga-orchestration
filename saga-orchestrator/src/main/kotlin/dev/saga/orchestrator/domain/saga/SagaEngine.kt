package dev.saga.orchestrator.domain.saga

import java.time.Duration
import java.time.Instant

class SagaEngine<D>(
    val definition: SagaDefinition<D>,
    private val replyTimeout: Duration,
    private val maxAttempts: Int,
) {
    private val steps get() = definition.steps

    fun start(id: String, data: D, now: Instant, context: Map<String, String> = emptyMap()): SagaDecision.Advance<D> {
        val initial = SagaInstance(
            id = id,
            type = definition.name,
            data = data,
            status = SagaStatus.RUNNING,
            stepStates = steps.map { StepState.PENDING },
            awaiting = null,
            failureReason = null,
            createdAt = now,
            updatedAt = now,
            context = context,
        )
        return forwardFrom(initial, lastCompleted = -1, now, listOf("Saga ${definition.name} iniciada"))
    }

    fun onReply(instance: SagaInstance<D>, reply: SagaReply, now: Instant): SagaDecision<D> {
        val awaiting = instance.awaiting
        if (instance.status.terminal || awaiting == null) {
            return SagaDecision.Ignore("La saga ya terminó (${instance.status}): se descarta ${reply.commandType}")
        }
        if (awaiting.commandType != reply.commandType) {
            return SagaDecision.Ignore(
                "Se esperaba la respuesta a ${awaiting.commandType} pero llegó la de ${reply.commandType}: " +
                    "se descarta (respuesta tardía o duplicada)",
            )
        }

        val index = awaiting.stepIndex
        val step = steps[index]

        if (!awaiting.compensating) {
            return when {
                reply.success -> {
                    val updated = instance.copy(
                        data = step.onSuccess(instance.data, reply.data),
                        stepStates = instance.stepStates.replace(index, StepState.SUCCEEDED),
                    )
                    forwardFrom(updated, index, now, listOf("${step.name} completado por ${step.participant}"))
                }

                step.kind == StepKind.RETRIABLE ->
                    resend(instance, now, "${step.name} falló (${reply.reason}). Es RETRIABLE: se reintenta, nunca se compensa")

                else -> {
                    val failed = instance.copy(
                        status = SagaStatus.COMPENSATING,
                        failureReason = reply.reason,
                        stepStates = instance.stepStates.mapIndexed { i, state ->
                            when {
                                i == index -> StepState.FAILED
                                state == StepState.PENDING -> StepState.SKIPPED
                                else -> state
                            }
                        },
                    )
                    compensateFrom(
                        failed, index - 1, now,
                        listOf("${step.name} falló: ${reply.reason}. Se compensan los pasos anteriores en orden inverso"),
                    )
                }
            }
        }

        return if (reply.success) {
            val updated = instance.copy(stepStates = instance.stepStates.replace(index, StepState.COMPENSATED))
            compensateFrom(updated, index - 1, now, listOf("${step.compensationName} completada: ${step.name} deshecho"))
        } else {
            resend(instance, now, "La compensación ${step.compensationName} falló (${reply.reason}): las compensaciones se reintentan hasta tener éxito")
        }
    }

    fun onTimeout(instance: SagaInstance<D>, now: Instant): SagaDecision<D> {
        val awaiting = instance.awaiting
            ?: return SagaDecision.Ignore("No está esperando ninguna respuesta")
        if (instance.status.terminal) return SagaDecision.Ignore("La saga ya terminó")
        if (Duration.between(awaiting.sentAt, now) < replyTimeout) return SagaDecision.Ignore("Todavía dentro del timeout")

        if (awaiting.attempts >= maxAttempts) {
            val reason = "Sin respuesta de ${awaiting.participant} a ${awaiting.commandType} tras ${awaiting.attempts} " +
                "intentos: requiere intervención manual"
            return SagaDecision.Advance(
                instance.copy(
                    status = SagaStatus.FAILED,
                    awaiting = null,
                    failureReason = reason,
                    stepStates = instance.stepStates.replace(awaiting.stepIndex, StepState.FAILED),
                    updatedAt = now,
                ),
                command = null,
                notes = listOf(reason),
            )
        }
        return resend(
            instance, now,
            "Timeout: ${awaiting.participant} no respondió ${awaiting.commandType} en ${replyTimeout.seconds}s " +
                "(intento ${awaiting.attempts}/$maxAttempts). Se reenvía el comando",
        )
    }

    private fun forwardFrom(
        instance: SagaInstance<D>,
        lastCompleted: Int,
        now: Instant,
        notes: List<String>,
    ): SagaDecision.Advance<D> {
        val states = instance.stepStates.toMutableList()
        val allNotes = notes.toMutableList()
        var next = lastCompleted + 1

        while (next < steps.size && steps[next].action == null) {
            states[next] = StepState.SUCCEEDED
            allNotes += "${steps[next].name}: ya realizado como transacción local de ${steps[next].participant}"
            next++
        }

        if (next >= steps.size) {
            allNotes += "Todos los pasos completados: saga COMPLETED"
            return SagaDecision.Advance(
                instance.copy(status = SagaStatus.COMPLETED, stepStates = states, awaiting = null, updatedAt = now),
                command = null,
                notes = allNotes,
            )
        }

        val step = steps[next]
        val command = step.action!!(instance.data)
        states[next] = StepState.RUNNING
        return SagaDecision.Advance(
            instance.copy(
                stepStates = states,
                awaiting = AwaitingReply(next, command.type, command.participant, compensating = false, sentAt = now, attempts = 1),
                updatedAt = now,
            ),
            command = command,
            notes = allNotes,
        )
    }

    private fun compensateFrom(
        instance: SagaInstance<D>,
        fromIndex: Int,
        now: Instant,
        notes: List<String>,
    ): SagaDecision.Advance<D> {
        for (i in fromIndex downTo 0) {
            val step = steps[i]
            val compensation = step.compensation
            if (instance.stepStates[i] == StepState.SUCCEEDED && compensation != null) {
                val command = compensation(instance.data, instance.failureReason ?: "Falla en la saga")
                return SagaDecision.Advance(
                    instance.copy(
                        stepStates = instance.stepStates.replace(i, StepState.COMPENSATING),
                        awaiting = AwaitingReply(i, command.type, command.participant, compensating = true, sentAt = now, attempts = 1),
                        updatedAt = now,
                    ),
                    command = command,
                    notes = notes,
                )
            }
        }
        return SagaDecision.Advance(
            instance.copy(status = SagaStatus.COMPENSATED, awaiting = null, updatedAt = now),
            command = null,
            notes = notes + "Todas las compensaciones completadas: saga COMPENSATED (el sistema volvió a un estado consistente)",
        )
    }

    private fun resend(instance: SagaInstance<D>, now: Instant, note: String): SagaDecision.Advance<D> {
        val awaiting = instance.awaiting!!
        val step = steps[awaiting.stepIndex]
        val command = if (awaiting.compensating) {
            step.compensation!!(instance.data, instance.failureReason ?: "Falla en la saga")
        } else {
            step.action!!(instance.data)
        }
        return SagaDecision.Advance(
            instance.copy(awaiting = awaiting.copy(sentAt = now, attempts = awaiting.attempts + 1), updatedAt = now),
            command = command,
            notes = listOf(note),
        )
    }

    private fun <T> List<T>.replace(index: Int, value: T): List<T> =
        mapIndexed { i, current -> if (i == index) value else current }
}
