package ru.aigram.speech.reply

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class VoiceTestClock(var current: Instant = Instant.parse("2026-10-21T12:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = current
}
