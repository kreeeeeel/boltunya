package ru.boltunya.application

import jakarta.inject.Inject
import ru.boltunya.http.ExitAction

private const val RESPONSE_FLUSH_DELAY_MS = 300L

class ProcessExit @Inject constructor() : ExitAction {
    override fun request() {
        Thread.ofPlatform().start {
            Thread.sleep(RESPONSE_FLUSH_DELAY_MS)
            kotlin.system.exitProcess(0)
        }
    }
}
