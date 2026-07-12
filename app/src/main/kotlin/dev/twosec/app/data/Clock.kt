package dev.twosec.app.data

fun interface Clock {
    fun now(): Long
}

class SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

class FakeClock(initialNow: Long = 0L) : Clock {
    var nowMs: Long = initialNow
        private set

    override fun now(): Long = nowMs

    fun advance(deltaMs: Long) {
        require(deltaMs >= 0) { "advance must be non-negative" }
        nowMs += deltaMs
    }

    fun set(now: Long) {
        nowMs = now
    }
}
