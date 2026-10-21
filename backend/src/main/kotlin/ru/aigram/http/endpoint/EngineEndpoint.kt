package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component
import ru.aigram.ai.EngineManager
import ru.aigram.http.ApiRoute
import ru.aigram.http.RequestReader

private const val MAX_MODEL_NAME_CHARACTERS = 120

@Component
class EngineEndpoint @Inject constructor(private val engine: EngineManager, private val input: RequestReader) {
    fun devices() = engine.devices()

    fun act(route: ApiRoute, body: JsonObject) {
        when (route) {
            ApiRoute.ENGINE_INSTALL -> engine.installEngine()
            ApiRoute.ENGINE_START -> engine.start()
            ApiRoute.MODEL_PULL -> engine.pull(input.text(body, "model", MAX_MODEL_NAME_CHARACTERS))
            ApiRoute.MODEL_CANCEL -> engine.cancelDownload()
            else -> Unit
        }
    }
}
