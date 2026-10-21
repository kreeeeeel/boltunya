package ru.aigram.controller.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.settings.VoiceReplyMode
import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.speech.reply.VoiceDraftStore
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechEndpointTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `voice settings persist independently and reject invalid partial updates atomically`() =
        withServer { settings, _, _, base ->
            assertFalse(settings.get().voiceInputEnabled)
            assertEquals(VoiceReplyMode.TEXT, settings.get().voiceReplyMode)
            assertEquals(
                200,
                request(
                    base,
                    "/api/settings",
                    "PATCH",
                    """{"voiceInputEnabled":true,"voiceReplyMode":"mirror","voiceReplyProbability":37,"voiceSpeechRate":1.2}""",
                ).statusCode(),
            )
            assertEquals(VoiceReplyMode.MIRROR, settings.get().voiceReplyMode)
            assertEquals(37, SettingsStore(directory).get().voiceReplyProbability)
            assertEquals(200, request(base, "/api/settings", "PATCH", """{"showTyping":false}""").statusCode())
            val before = settings.get()
            Stream.of(
                """{"voiceReplyMode":null}""",
                """{"voiceReplyMode":"unknown"}""",
                """{"voiceReplyProbability":101}""",
                """{"voiceSpeechRate":0.1}""",
                """{"voiceRecognitionModel":"../model"}""",
                """{"voiceSynthesisModel":"whisper-tiny"}""",
                """{"voiceInputEnabled":"true"}""",
            ).forEach { body ->
                assertEquals(400, request(base, "/api/settings", "PATCH", body).statusCode(), body)
                assertEquals(before, settings.get())
            }
            assertTrue(settings.get().paused)
        }

    @Test
    fun `speech catalog requires local authentication and never installs an unlisted source`() =
        withServer { _, gateway, _, base ->
            assertEquals(401, request(base, "/api/speech", cookie = false).statusCode())
            val state = request(base, "/api/speech")
            assertEquals(200, state.statusCode())
            assertTrue(state.body().contains("whisper-tiny"))
            assertFalse(state.body().contains("downloadUrl"))
            assertEquals(400, request(base, "/api/speech/assets/unknown/install", "POST", "{}").statusCode())
            assertEquals(
                409,
                request(
                    base,
                    "/api/speech/drafts",
                    "POST",
                    """{"chatId":"42","text":"Synthetic speech"}""",
                ).statusCode(),
            )
            assertEquals(400, request(base, "/api/speech/drafts", "POST", """{"chatId":"42","text":""}""").statusCode())
            Stream.of(
                """{"chatId":"42","text":"Text","modelId":null}""",
                """{"chatId":"42","text":"Text","modelId":""}""",
                """{"chatId":"42","text":"Text","modelId":" "}""",
                """{"chatId":"42","text":"Text","speed":null}""",
                """{"chatId":"42","text":"Text","speed":0.49}""",
                """{"chatId":"42","text":"Text","speed":2.01}""",
                """{"chatId":"42","text":"Text","speed":"1.0"}""",
            ).forEach { body ->
                assertEquals(400, request(base, "/api/speech/drafts", "POST", body).statusCode(), body)
            }
            assertTrue(gateway.sentVoices.isEmpty())
        }

    @Test
    fun `authenticated voice preview supports ranges and explicit delivery is idempotent`() =
        withServer { _, gateway, server, base ->
            val store = server.bean(VoiceDraftStore::class.java)
            val path = store.output()
            Files.writeString(path, "OggSsynthetic-voice")
            val voice = store.register("100", "42", OutgoingVoiceNote(path, 2, byteArrayOf(0)))
            val preview = request(base, voice.url, range = "bytes=0-3")
            assertEquals(206, preview.statusCode())
            assertEquals("OggS", preview.body())
            assertEquals("audio/ogg", preview.headers().firstValue("Content-Type").orElse(""))
            assertEquals(401, request(base, voice.url, cookie = false).statusCode())
            val body = """{"voiceId":"${voice.id}","replyToId":"1","topicId":8}"""
            assertEquals(404, request(base, "/api/chats/43/voice", "POST", body).statusCode())
            assertEquals(200, request(base, "/api/chats/42/voice", "POST", body).statusCode())
            assertEquals(200, request(base, "/api/chats/42/voice", "POST", body).statusCode())
            assertEquals(1, gateway.sentVoices.size)
            assertEquals(listOf(false), gateway.voiceAutomated)
            assertEquals(listOf("1"), gateway.voiceReplyIds)
            assertEquals(listOf(8), gateway.voiceTopicIds)
            assertTrue(gateway.sent.isEmpty())
        }

    private fun request(
        base: String,
        path: String,
        method: String = "GET",
        body: String? = null,
        cookie: Boolean = true,
        range: String? = null,
    ): HttpResponse<String> = HttpClient.newHttpClient().use { http ->
        val request = HttpRequest.newBuilder(URI.create(base + path))
        if (cookie) {
            request.header("Cookie", "tiho=speech-test")
        }
        if (range != null) {
            request.header("Range", range)
        }
        http.send(
            request.method(
                method,
                body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody(),
            ).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun withServer(action: (SettingsStore, FakeTelegramGateway, TestWebServer, String) -> Unit) {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("Unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "speech-test") {
                }.use { server ->
                    action(settings, gateway, server, server.start().substringBefore("/?"))
                }
            }
        }
    }
}
