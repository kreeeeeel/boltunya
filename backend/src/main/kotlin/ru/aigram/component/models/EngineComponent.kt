package ru.aigram.component.models

import org.springframework.stereotype.Component
import ru.aigram.ai.EngineManager
import ru.aigram.exception.impl.MalformedPayloadException

private const val MAX_MODEL_NAME_CHARACTERS = 120

@Component
class EngineComponent(private val engine: EngineManager) {
    fun devices() = engine.devices()
    fun install() = engine.installEngine()
    fun start() = engine.start()
    fun restart() = engine.restart()
    fun cancelDownload() = engine.cancelDownload()
    fun pull(model: String) {
        if (model.isBlank() || model.length > MAX_MODEL_NAME_CHARACTERS) {
            throw MalformedPayloadException()
        }
        engine.pull(model)
    }
}
