package ru.aigram.automation.activity

import org.springframework.stereotype.Component
import ru.aigram.domain.events.ActivityPhase

@Component
class ReplyTraceScope {
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
