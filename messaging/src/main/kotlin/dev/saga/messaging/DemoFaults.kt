package dev.saga.messaging

data class DemoFaults(
    val paymentRejected: Boolean = false,
    val paymentDelayMs: Long = 0,
    val inventoryDown: Boolean = false,
    val duplicateCommands: Boolean = false,
) {
    fun toHeaders(): Map<String, String> = buildMap {
        if (paymentRejected) put(PAYMENT_REJECT, "true")
        if (paymentDelayMs > 0) put(PAYMENT_DELAY_MS, paymentDelayMs.coerceIn(0, MAX_DELAY_MS).toString())
        if (inventoryDown) put(INVENTORY_DOWN, "true")
        if (duplicateCommands) put(DUPLICATE_COMMANDS, "true")
    }

    companion object {
        const val PAYMENT_REJECT = "fault.payment.reject"
        const val PAYMENT_DELAY_MS = "fault.payment.delayMs"
        const val INVENTORY_DOWN = "fault.inventory.down"
        const val DUPLICATE_COMMANDS = "fault.orchestrator.duplicateCommands"
        const val MAX_DELAY_MS = 15_000L

        fun from(headers: Map<String, String>) = DemoFaults(
            paymentRejected = headers[PAYMENT_REJECT] == "true",
            paymentDelayMs = headers[PAYMENT_DELAY_MS]?.toLongOrNull()?.coerceIn(0, MAX_DELAY_MS) ?: 0,
            inventoryDown = headers[INVENTORY_DOWN] == "true",
            duplicateCommands = headers[DUPLICATE_COMMANDS] == "true",
        )
    }
}

object MessageContext {
    private val current = ThreadLocal<Map<String, String>>()

    fun headers(): Map<String, String> = current.get() ?: emptyMap()

    fun faults(): DemoFaults = DemoFaults.from(headers())

    fun <T> with(headers: Map<String, String>, block: () -> T): T {
        val previous = current.get()
        current.set(headers)
        try {
            return block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }
}
