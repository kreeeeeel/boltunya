package ru.boltunya.memory

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AdvancingClock(private var now: Instant) : Clock() {
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = fixed(now, zone)
}
