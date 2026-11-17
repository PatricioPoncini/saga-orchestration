package dev.saga.payment.adapter.`in`.web

import dev.saga.payment.application.port.`in`.ListPaymentsQuery
import dev.saga.payment.domain.Payment
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PaymentController(private val payments: ListPaymentsQuery) {
    @GetMapping("/payments")
    fun list(): List<Payment> = payments.listPayments()
}
