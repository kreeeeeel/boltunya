package ru.boltunya.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.impl.EngineHttpFailedException
import ru.boltunya.exception.impl.EngineUnavailableException
import ru.boltunya.exception.impl.ModelDownloadHttpFailedException
import ru.boltunya.exception.impl.ModelPullFailedException
import ru.boltunya.exception.impl.RegistryRedirectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private const val WARM_CONTEXT_TOKENS = 8192
private const val LOCAL_OLLAMA_PORT = 11434
private val MODEL_WARM_TIMEOUT = Duration.ofMinutes(30)
private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "[::1]", "::1")

class OllamaRuntime(private val baseUrl: String) {
    private val endpoint = URI.create(baseUrl)
    val isLocalServer: Boolean = endpoint.host in LOOPBACK_HOSTS
    val isDefaultLocalServer: Boolean = isLocalServer && endpoint.scheme == "http" && endpoint.port == LOCAL_OLLAMA_PORT
    private val activeModels = ConcurrentHashMap.newKeySet<String>()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    fun track(model: String) {
        activeModels.add(model)
    }

    fun warm(model: String) {
        track(model)
        request(
            "/api/generate",
            buildJsonObject {
                put("model", model)
                put("keep_alive", "20m")
                put("stream", false)
                put("options", buildJsonObject { put("num_ctx", WARM_CONTEXT_TOKENS) })
            },
            MODEL_WARM_TIMEOUT,
        )
    }

    fun installed(): Set<String> {
        val response = request("/api/tags", null, Duration.ofSeconds(4))
        return response["models"]?.jsonArray?.stream()?.map {
            (it.jsonObject["name"] ?: it.jsonObject["model"])?.jsonPrimitive?.contentOrNull.orEmpty()
                .removePrefix("registry.ollama.ai/").removePrefix("library/")
        }?.toList()?.toSet() ?: emptySet()
    }

    fun pull(model: String, cancellation: DownloadCancellation = DownloadCancellation(), onProgress: (Int) -> Unit) {
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
        }
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/api/pull"))
            .timeout(Duration.ofMinutes(45))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { input ->
            cancellation.attach(input)
            if (response.statusCode() != 200) {
                throw ModelDownloadHttpFailedException(response.statusCode())
            }
            input.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    cancellation.check()
                    val item = Json.parseToJsonElement(line).jsonObject
                    if (item["error"] != null) {
                        val error = item["error"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (error.contains("redirect target not allowed")) {
                            throw RegistryRedirectException()
                        }
                        throw ModelPullFailedException()
                    }
                    val total = item["total"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val done = item["completed"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    if (total > 0) {
                        onProgress((done / total * 100).toInt())
                    }
                }
            }
        }
    }

    fun releaseModels() {
        activeModels.stream().forEach { model ->
            try {
                request(
                    "/api/generate",
                    buildJsonObject {
                        put("model", model)
                        put("keep_alive", 0)
                    },
                    Duration.ofSeconds(3),
                )
            } catch (exception: IntegrationException) {
                System.err.println("Model unload unavailable: " + exception.javaClass.simpleName)
            }
        }
        activeModels.clear()
    }

    private fun request(path: String, body: JsonObject?, timeout: Duration): JsonObject {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
        if (body != null) {
            builder.header(
                "Content-Type",
                "application/json",
            ).POST(HttpRequest.BodyPublishers.ofString(body.toString()))
        }
        val response = try {
            http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (exception: java.io.IOException) {
            throw EngineUnavailableException(exception)
        }
        if (response.statusCode() != 200) {
            throw EngineHttpFailedException(response.statusCode())
        }
        return Json.parseToJsonElement(response.body()).jsonObject
    }
}
