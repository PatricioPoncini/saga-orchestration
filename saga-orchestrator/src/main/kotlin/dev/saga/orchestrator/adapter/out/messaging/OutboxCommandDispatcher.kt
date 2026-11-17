package dev.saga.orchestrator.adapter.out.messaging

import dev.saga.messaging.DemoFaults
import dev.saga.messaging.Outbox
import dev.saga.messaging.Topology
import dev.saga.orchestrator.application.port.out.CommandDispatcher
import dev.saga.orchestrator.domain.saga.SagaCommand
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class OutboxCommandDispatcher(private val outbox: Outbox) : CommandDispatcher {
    override fun dispatch(sagaId: String, command: SagaCommand, context: Map<String, String>) {
        val messageId = UUID.randomUUID().toString()
        val copies = if (DemoFaults.from(context).duplicateCommands) 2 else 1
        repeat(copies) {
            outbox.send(
                Topology.COMMANDS_EXCHANGE, command.participant, sagaId, command.type, command.payload,
                messageId = messageId,
                headers = context,
            )
        }
    }
}
