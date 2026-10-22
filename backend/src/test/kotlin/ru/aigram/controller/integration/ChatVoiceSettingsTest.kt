package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.VoiceSettingsMode
import ru.aigram.domain.settings.VoiceReplyMode
import ru.aigram.speech.reply.voicePreferences
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatVoiceSettingsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `typed chat voice overrides persist without overwriting global or unrelated preferences`() = withServer {
            settings,
            base,
            http,
        ->
        val initial = settings.get()
        val rule = Json.parseToJsonElement(
            """{"instructions":"Synthetic chat instructions","proactiveMode":"disabled",
                |"proactiveMessageMode":"text","proactiveText":"Synthetic initiative",
                |"voiceSettingsMode":"override","voiceInputEnabled":true,"voiceReplyMode":"mirror",
                |"voiceReplyProbability":17,"voiceRecognitionModel":"whisper-tiny",
                |"voiceSynthesisModel":"piper-en-amy","voiceSpeechRate":1.3}
            """.trimMargin(),
        ).jsonObject
        assertEquals(200, request(http, base, "/api/chats/42/rule", "PUT", rule).statusCode())
        val saved = settings.get()
        assertEquals(initial.copy(rules = saved.rules), saved)
        val chat = settings.rule("42")
        assertEquals(VoiceSettingsMode.OVERRIDE, chat.voiceSettingsMode)
        assertEquals(VoiceReplyMode.MIRROR, chat.voiceReplyMode)
        assertEquals(17, chat.voiceReplyProbability)
        assertEquals("piper-en-amy", chat.voiceSynthesisModel)
        assertEquals(1.3, chat.voiceSpeechRate)
        assertTrue(chat.voiceInputEnabled)

        assertEquals(
            200,
            request(
                http,
                base,
                "/api/settings",
                "PATCH",
                JsonObject(
                    mapOf(
                        "showTyping" to JsonPrimitive(false),
                        "voiceSynthesisModel" to JsonPrimitive("piper-ru-dmitri"),
                    ),
                ),
            ).statusCode(),
        )
        assertEquals(chat, settings.rule("42"))
        assertEquals("piper-en-amy", settings.get().voicePreferences(chat).synthesisModel)
        assertEquals("Synthetic chat instructions", SettingsStore(directory).rule("42").instructions)

        listOf(
            "voiceSettingsMode",
            "voiceInputEnabled",
            "voiceReplyMode",
            "voiceReplyProbability",
            "voiceRecognitionModel",
            "voiceSynthesisModel",
            "voiceSpeechRate",
        ).stream().forEach { name ->
            assertRejected(settings, http, base, JsonObject(rule + (name to JsonNull)))
        }
        listOf(
            "voiceSettingsMode" to JsonPrimitive("unknown"),
            "voiceInputEnabled" to JsonPrimitive("true"),
            "voiceReplyMode" to JsonPrimitive("inherit"),
            "voiceReplyProbability" to JsonPrimitive(-1),
            "voiceReplyProbability" to JsonPrimitive(101),
            "voiceReplyProbability" to JsonPrimitive(1.5),
            "voiceRecognitionModel" to JsonPrimitive("piper-en-amy"),
            "voiceSynthesisModel" to JsonPrimitive("whisper-tiny"),
            "voiceSynthesisModel" to JsonPrimitive(" "),
            "voiceSpeechRate" to JsonPrimitive(0.49),
            "voiceSpeechRate" to JsonPrimitive(2.01),
            "voiceSpeechRate" to JsonPrimitive("1.0"),
        ).stream().forEach { (name, value) ->
            assertRejected(settings, http, base, JsonObject(rule + (name to value)))
        }
    }

    private fun assertRejected(settings: SettingsStore, http: HttpClient, base: String, rule: JsonObject) {
        val before = settings.get()
        assertEquals(400, request(http, base, "/api/chats/42/rule", "PUT", rule).statusCode(), rule.toString())
        assertEquals(before, settings.get())
    }

    private fun request(
        http: HttpClient,
        base: String,
        path: String,
        method: String,
        body: JsonObject,
    ): HttpResponse<String> = http.send(
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Cookie", "tiho=chat-voice-test")
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body.toString())).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun withServer(action: (SettingsStore, String, HttpClient) -> Unit) {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("Unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "chat-voice-test") {
                }.use { server ->
                    HttpClient.newHttpClient().use { http ->
                        action(settings, server.start().substringBefore("/?"), http)
                    }
                }
            }
        }
    }
}
