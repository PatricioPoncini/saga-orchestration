package dev.saga.inventory.adapter.`in`.demo

import dev.saga.messaging.DemoFaults
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class SimulatedOutages {
    data class Status(val down: Boolean, val since: Instant?, val recoversAt: Instant?)

    private val firstSeen = ConcurrentHashMap<String, Instant>()
    private val recovered: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun isDown(sagaId: String, faults: DemoFaults, now: Instant = Instant.now()): Boolean {
        if (!faults.inventoryDown || sagaId in recovered) return false
        val since = firstSeen.computeIfAbsent(sagaId) { now }
        if (Duration.between(since, now) >= AUTO_RECOVER) {
            recovered += sagaId
            return false
        }
        return true
    }

    fun recover(sagaId: String) {
        recovered += sagaId
    }

    fun status(sagaId: String, now: Instant = Instant.now()): Status {
        val since = firstSeen[sagaId] ?: return Status(false, null, null)
        val recoversAt = since + AUTO_RECOVER
        val down = sagaId !in recovered && now < recoversAt
        return Status(down, since, if (down) recoversAt else null)
    }

    @Scheduled(fixedDelay = 60_000)
    fun forgetOldOutages() {
        val limit = Instant.now() - RETENTION
        firstSeen.entries.removeIf { (sagaId, since) ->
            (since < limit).also { old -> if (old) recovered -= sagaId }
        }
    }

    companion object {
        val AUTO_RECOVER: Duration = Duration.ofMinutes(2)
        val RETENTION: Duration = Duration.ofMinutes(20)
    }
}
