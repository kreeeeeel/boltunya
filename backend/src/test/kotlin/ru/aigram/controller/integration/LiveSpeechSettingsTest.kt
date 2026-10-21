package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveSpeechSettingsTest {
    @Test
    fun `real settings API imports a Piper voice and sends an exact manual draft through a fake gateway once`() {
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
                            val modelId = importVoice(http, base, archive)
                            preview(http, base, modelId, verified)
                            assertEquals(before, settings.get())
                            assertTrue(gateway.sent.isEmpty())
                            assertTrue(gateway.sentVoices.isEmpty())

                            manualDraft(http, base, modelId, verified, gateway)
                            assertEquals(before, settings.get())
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

    private fun importVoice(http: HttpClient, base: String, archive: Path): String {
        val uploaded = http.send(
            HttpRequest.newBuilder(URI.create(base + "/api/uploads"))
                .header("Cookie", "tiho=speech-live")
                .header("X-File-Name", "local-voice.tar.bz2")
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofFile(archive)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, uploaded.statusCode())
        val uploadId = Json.parseToJsonElement(uploaded.body()).jsonObject.getValue("id").jsonPrimitive.content
        val imported = json(
            http,
            base,
            "/api/speech/models/import",
            """{"uploadId":"$uploadId","title":"Synthetic imported voice","language":"ru"}""",
        )
        val assets = imported.getValue("assets") as JsonArray
        val custom = assertNotNull(
            assets.stream().map { it.jsonObject }
                .filter { it["userProvided"]?.jsonPrimitive?.content == "true" }
                .findFirst().orElse(null),
        )
        val modelId = custom.getValue("id").jsonPrimitive.content
        assertTrue(modelId.startsWith("custom-"))
        assertEquals("true", custom.getValue("installed").jsonPrimitive.content)
        return modelId
    }

    private fun preview(http: HttpClient, base: String, modelId: String, verified: Path) {
        val queued = json(
            http,
            base,
            "/api/speech/preview",
            """{"text":"Привет! Это настоящий тест голоса в настройках.","modelId":"$modelId","speed":1.2}""",
        )
        val result = awaitResult(http, base, queued)
        assertEquals("Привет! Это настоящий тест голоса в настройках.", result.getValue("text").jsonPrimitive.content)
        assertEquals(modelId, result.getValue("model").jsonPrimitive.content)
        Files.write(verified.resolve("settings-preview.ogg"), audio(http, base, result.getValue("voice").jsonObject))
    }

    private fun manualDraft(
        http: HttpClient,
        base: String,
        modelId: String,
        verified: Path,
        gateway: FakeTelegramGateway,
    ) {
        val text = "Привет! Это ручное голосовое сообщение."
        val queued = json(
            http,
            base,
            "/api/speech/drafts",
            """{"chatId":"42","text":"$text","modelId":"$modelId","speed":1.3}""",
        )
        val result = awaitResult(http, base, queued)
        assertEquals(text, result.getValue("text").jsonPrimitive.content)
        assertEquals(modelId, result.getValue("model").jsonPrimitive.content)
        val voice = result.getValue("voice").jsonObject
        val bytes = audio(http, base, voice)
        Files.write(verified.resolve("settings-manual-draft.ogg"), bytes)
        assertTrue(gateway.sent.isEmpty())
        assertTrue(gateway.sentVoices.isEmpty())

        val voiceId = voice.getValue("id").jsonPrimitive.content
        val body = """{"voiceId":"$voiceId","replyToId":"1","topicId":0}"""
        assertEquals("true", json(http, base, "/api/chats/42/voice", body).getValue("ok").jsonPrimitive.content)
        assertEquals(1, gateway.sentVoices.size)
        assertEquals(listOf(false), gateway.voiceAutomated)
        assertEquals(listOf("1"), gateway.voiceReplyIds)
        assertEquals(listOf(0), gateway.voiceTopicIds)
        assertTrue(gateway.sent.isEmpty())
        assertContentEquals(bytes, Files.readAllBytes(gateway.sentVoices.single().path))

        assertEquals("true", json(http, base, "/api/chats/42/voice", body).getValue("ok").jsonPrimitive.content)
        assertEquals(1, gateway.sentVoices.size)
        assertEquals(listOf(false), gateway.voiceAutomated)
        assertTrue(gateway.sent.isEmpty())
    }

    private fun awaitResult(http: HttpClient, base: String, queued: JsonObject): JsonObject {
        val jobId = queued.getValue("id").jsonPrimitive.content
        var job = queued
        awaitSpeech(120) {
            job = json(http, base, "/api/preview/jobs/$jobId")
            job.getValue("status").jsonPrimitive.content in setOf("ready", "error", "cancelled")
        }
        assertEquals("ready", job.getValue("status").jsonPrimitive.content, job.toString())
        return job.getValue("result").jsonObject
    }

    private fun audio(http: HttpClient, base: String, voice: JsonObject): ByteArray {
        val response = http.send(
            HttpRequest.newBuilder(URI.create(base + voice.getValue("url").jsonPrimitive.content))
                .header("Cookie", "tiho=speech-live").GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        assertEquals(200, response.statusCode())
        val bytes = response.body()
        assertTrue(bytes.size >= 4)
        assertEquals("OggS", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        return bytes
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
