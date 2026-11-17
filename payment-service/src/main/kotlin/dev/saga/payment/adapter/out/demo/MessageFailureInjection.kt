package dev.saga.payment.adapter.out.demo

import dev.saga.messaging.MessageContext
import dev.saga.payment.application.port.out.FailureInjectionPort
import org.springframework.stereotype.Component

@Component
class MessageFailureInjection : FailureInjectionPort {
    override fun forceRejection(): Boolean = MessageContext.faults().paymentRejected
}
