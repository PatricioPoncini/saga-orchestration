package dev.saga.messaging

object Topology {
    const val COMMANDS_EXCHANGE = "saga.commands"
    const val REPLIES_EXCHANGE = "saga.replies"
    const val TRACE_EXCHANGE = "saga.trace"
    const val DEAD_LETTER_EXCHANGE = "saga.dlx"
    const val RETRY_EXCHANGE = "saga.retry"
    const val INVENTORY_RETRY_QUEUE = "inventory.retry"
    const val RETRY_DELAY_MS = 2_000

    const val ORDER_QUEUE = "order.commands"
    const val PAYMENT_QUEUE = "payment.commands"
    const val INVENTORY_QUEUE = "inventory.commands"
    const val ORCHESTRATOR_QUEUE = "orchestrator.commands"
    const val REPLIES_QUEUE = "orchestrator.replies"
    const val TRACE_QUEUE = "orchestrator.trace"
    const val DEAD_LETTER_QUEUE = "saga.dead-letters"

    const val ORDER = "order"
    const val PAYMENT = "payment"
    const val INVENTORY = "inventory"
    const val ORCHESTRATOR = "orchestrator"
    const val DEAD = "dead"
}
