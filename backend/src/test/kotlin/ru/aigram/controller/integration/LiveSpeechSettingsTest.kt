package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.exception.impl.SpeechInferenceFailedException
import ru.aigram.speech.awaitSpeech
import ru.aigram.speech.verifiedSpeechCatalog
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveSpeechSettingsTest {
    @Test
    fun `real settings API imports a Piper package and speaks the exact sample without Telegram delivery`() {
        assumeTrue(System.getenv("AIGRAM_LIVE_SPEECH") == "true")
        val verified = Path.of("../build/speech-verification").toAbsolutePath().normalize()
        assumeTrue(Files.isDirectory(verified))
        val directory = Files.createTempDirectory(verified, "settings-")
        try {
            verifiedSpeechCatalog(directory, verified, listOf("sherpa-runtime")).close()
            val settings = SettingsStore(directory)
            val before = settings.get()
            val gateway = FakeTelegramGateway()
            val ai = AiTestServices("http://127.0.0.1:1")
            EngineManager(directory, ai.runtime).use { engine ->
                ReplyCoordinator(settings, gateway) { _, _ -> throw SpeechInferenceFailedException() }.use { queue ->
                    TestWebServerFactory().create(directory, settings, gateway, engine, ai, queue, "speech-live") {
                    }.use { server ->
                        val base = server.start().substringBefore("/?")
                        HttpClient.newHttpClient().use { http ->
                            val archive = verified.resolve("vits-piper-ru_RU-irina-medium-int8.tar.bz2")
                            val uploaded = http.send(
                                HttpRequest.newBuilder(URI.create(base + "/api/uploads"))
                                    .header("Cookie", "tiho=speech-live")
                                    .header("X-File-Name", "local-voice.tar.bz2")
                                    .header("Content-Type", "application/octet-stream")
                                    .POST(HttpRequest.BodyPublishers.ofFile(archive)).build(),
                                HttpResponse.BodyHandlers.ofString(),
                            )
                            assertEquals(200, uploaded.statusCode())
                            val uploadId = Json.parseToJsonElement(uploaded.body()).jsonObject
                                .getValue("id").jsonPrimitive.content
                            val imported = json(
                                http,
                                base,
                                "/api/speech/models/import",
                                """{"uploadId":"$uploadId","title":"Synthetic imported voice","language":"ru"}""",
                            )
                            val assets = imported.getValue("assets") as kotlinx.serialization.json.JsonArray
                            val custom = assets.stream().map { it.jsonObject }
                                .filter {
                                    it["userProvided"]?.jsonPrimitive?.content == "true"
                                }.findFirst().orElseThrow()
                            val modelId = custom.getValue("id").jsonPrimitive.content
                            assertTrue(modelId.startsWith("custom-"))
                            assertEquals("true", custom.getValue("installed").jsonPrimitive.content)
                            val queued = json(
                                http,
                                base,
                                "/api/speech/preview",
                                """{"text":"Привет! Это настоящий тест голоса в настройках.","modelId":"$modelId","speed":1.2}""",
                            )
                            val jobId = queued.getValue("id").jsonPrimitive.content
                            var job = queued
                            awaitSpeech(120) {
                                job = json(http, base, "/api/preview/jobs/$jobId")
                                job.getValue("status").jsonPrimitive.content in setOf("ready", "error", "cancelled")
                            }
                            assertEquals("ready", job.getValue("status").jsonPrimitive.content, job.toString())
                            val result = job.getValue("result").jsonObject
                            assertEquals(
                                "Привет! Это настоящий тест голоса в настройках.",
                                result.getValue("text").jsonPrimitive.content,
                            )
                            val voice = result.getValue("voice").jsonObject
                            val audio = http.send(
                                HttpRequest.newBuilder(URI.create(base + voice.getValue("url").jsonPrimitive.content))
                                    .header("Cookie", "tiho=speech-live").GET().build(),
                                HttpResponse.BodyHandlers.ofByteArray(),
                            )
                            assertEquals(200, audio.statusCode())
                            assertEquals("OggS", audio.body().copyOfRange(0, 4).toString(Charsets.US_ASCII))
                            Files.write(verified.resolve("settings-preview.ogg"), audio.body())
                            assertEquals(before, settings.get())
                            assertTrue(gateway.sent.isEmpty())
                            assertTrue(gateway.sentVoices.isEmpty())
                        }
                    }
                }
            }
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun json(http: HttpClient, base: String, path: String, body: String? = null): JsonObject {
        val builder = HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", "tiho=speech-live")
        if (body != null) {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
        } else {
            builder.GET()
        }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode(), response.body())
        return Json.parseToJsonElement(response.body()).jsonObject
    }
}
