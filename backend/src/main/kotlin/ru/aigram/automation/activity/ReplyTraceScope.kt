package ru.aigram.automation.activity

import jakarta.inject.Inject

class ReplyTraceScope @Inject constructor() {
    private val current = ThreadLocal<ReplyTrace>()

    fun current(): ReplyTrace? = current.get()

    fun phase(value: ActivityPhase) {
        current.get()?.phase(value)
    }

    fun <T> capture(trace: ReplyTrace, action: () -> T): T {
        current.set(trace)
        try {
            return action()
        } finally {
            current.remove()
        }
    }
}
