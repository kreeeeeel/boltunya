package ru.aigram.application

import org.springframework.stereotype.Component
import ru.aigram.http.ExitAction

private const val RESPONSE_FLUSH_DELAY_MS = 300L

@Component
class ProcessExit : ExitAction {
    override fun request() {
        Thread.ofPlatform().start {
            Thread.sleep(RESPONSE_FLUSH_DELAY_MS)
            kotlin.system.exitProcess(0)
        }
    }
}
