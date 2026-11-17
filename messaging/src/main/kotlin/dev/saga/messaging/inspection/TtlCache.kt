package dev.saga.messaging.inspection

import java.time.Duration

class TtlCache<T : Any>(private val ttl: Duration) {
    @Volatile
    private var value: T? = null

    @Volatile
    private var loadedAt: Long = 0

    fun get(load: () -> T): T {
        val now = System.nanoTime()
        val current = value
        if (current != null && now - loadedAt < ttl.toNanos()) return current
        synchronized(this) {
            val again = value
            if (again != null && System.nanoTime() - loadedAt < ttl.toNanos()) return again
            return load().also {
                value = it
                loadedAt = System.nanoTime()
            }
        }
    }
}
