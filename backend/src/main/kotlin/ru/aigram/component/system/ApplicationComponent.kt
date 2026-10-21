package ru.aigram.component.system

import org.springframework.stereotype.Component
import ru.aigram.application.ExitAction

@Component
class ApplicationComponent(private val exit: ExitAction) {
    fun quit() = exit.request()
}
