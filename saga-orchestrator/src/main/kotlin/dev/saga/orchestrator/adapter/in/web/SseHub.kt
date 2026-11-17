package dev.saga.orchestrator.adapter.`in`.web

import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

@Component
class SseHub {
    private val subscriptions = ConcurrentHashMap<SseEmitter, String>()

    fun subscribe(sagaId: String?): SseEmitter {
        val emitter = SseEmitter(0L)
        subscriptions[emitter] = sagaId ?: NONE
        emitter.onCompletion { subscriptions.remove(emitter) }
        emitter.onTimeout { subscriptions.remove(emitter) }
        emitter.onError { subscriptions.remove(emitter) }
        emitter.send(SseEmitter.event().name("hello").data("connected"))
        return emitter
    }

    fun broadcast(sagaId: String, payload: Any) {
        subscriptions.forEach { (emitter, subscribedTo) ->
            if (subscribedTo == sagaId) send(emitter) { it.send(SseEmitter.event().name("timeline").data(payload, MediaType.APPLICATION_JSON)) }
        }
    }

    fun connections(): Int = subscriptions.size

    @Scheduled(fixedRate = 15_000)
    fun heartbeat() {
        subscriptions.keys.forEach { emitter -> send(emitter) { it.send(SseEmitter.event().comment("keep-alive")) } }
    }

    private fun send(emitter: SseEmitter, action: (SseEmitter) -> Unit) {
        try {
            action(emitter)
        } catch (e: Exception) {
            subscriptions.remove(emitter)
        }
    }

    private companion object {
        const val NONE = ""
    }
}
