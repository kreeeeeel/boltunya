package ru.aigram.controller.integration

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.events.ActivityDetails
import ru.aigram.domain.events.ActivityFeedback
import ru.aigram.domain.events.ActivityFeedbackRating
import ru.aigram.domain.events.ActivityFeedbackUpdate
import ru.aigram.exception.impl.ActivityFeedbackUnavailableException
import ru.aigram.storage.ActivityFeedbackStore
import ru.aigram.storage.SettingsStore
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityFeedbackEndpointTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `requires a local session final generated response and valid rating before persisting explicit feedback`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.update { it.copy(paused = false, replyDelaySeconds = 0, replyIntervalSeconds = 0, showTyping = false) }
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        val feedback = ActivityFeedbackStore(directory, Clock.systemUTC())
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway, feedback = feedback) { _, _ ->
                ReplyResult("Сгенерированный ответ", 1, 1.0)
            }
                .use { replies ->
                    replies.pauseQueue(true)
                    replies.onMessage(gateway.messages.first())
                    val id = replies.activities().single().id
                    TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "test-session") {
                    }.use { server ->
                        val base = server.start().substringBefore("/?")
                        val http = HttpClient.newHttpClient()
                        fun request(
                            path: String,
                            method: String = "GET",
                            body: String = "",
                            authorized: Boolean = true,
                        ): HttpResponse<String> {
                            val builder = HttpRequest.newBuilder(URI.create(base + path))
                                .method(method, HttpRequest.BodyPublishers.ofString(body))
                            if (authorized) {
                                builder.header("Cookie", "tiho=test-session")
                            }
                            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                        }
                        val path = "/api/activity/$id/feedback"
                        assertEquals(401, request(path, "PUT", """{"rating":"good"}""", false).statusCode())
                        assertEquals(401, request(path, "DELETE", authorized = false).statusCode())
                        assertEquals(
                            404,
                            request("/api/activity/missing/feedback", "PUT", """{"rating":"good"}""").statusCode(),
                        )
                        assertEquals(409, request(path, "PUT", """{"rating":"good"}""").statusCode())
                        assertEquals(409, request(path, "DELETE").statusCode())
                        assertFalse(replies.activities().single().feedbackAvailable)
                        replies.pauseQueue(false)
                        assertTrue(
                            Stream.generate { replies.activities().single().status }.limit(200).anyMatch { status ->
                                if (status == "sent") {
                                    true
                                } else {
                                    Thread.sleep(20)
                                    false
                                }
                            },
                        )
                        assertTrue(replies.activities().single().feedbackAvailable)
                        assertEquals(400, request(path, "PUT", """{"rating":"great"}""").statusCode())
                        assertEquals(400, request(path, "PUT", """{"rating":"bad","comment":null}""").statusCode())
                        assertEquals(400, request(path, "PUT", """{"rating":"bad","comment":123}""").statusCode())
                        val oversized = Json.encodeToString(mapOf("rating" to "bad", "comment" to "x".repeat(1001)))
                        assertEquals(400, request(path, "PUT", oversized).statusCode())
                        val saved = request(path, "PUT", """{"rating":"bad","comment":"Слишком длинно"}""")
                        assertEquals(200, saved.statusCode())
                        assertEquals(
                            ActivityFeedbackRating.BAD,
                            Json.decodeFromString<ActivityFeedback>(saved.body()).rating,
                        )
                        val changed = request(path, "PUT", """{"rating":"good"}""")
                        assertEquals("Слишком длинно", Json.decodeFromString<ActivityFeedback>(changed.body()).comment)
                        val details = Json.decodeFromString<ActivityDetails>(request("/api/activity/$id").body())
                        assertEquals(ActivityFeedbackRating.GOOD, details.feedback?.rating)
                        assertEquals(ActivityFeedbackRating.GOOD, replies.activities().single().feedbackRating)
                        assertEquals(
                            ActivityFeedbackRating.GOOD,
                            ActivityFeedbackStore(directory, Clock.systemUTC()).get(id)?.rating,
                        )
                        val persisted = Files.readString(directory.resolve("feedback/activity.json"))
                        assertFalse(persisted.contains("Сгенерированный ответ"))
                        assertFalse(persisted.contains("Привет"))
                        assertEquals(200, request(path, "DELETE").statusCode())
                        assertEquals(200, request(path, "DELETE").statusCode())
                        assertNull(replies.details(id).feedback)
                        assertNull(replies.activities().single().feedbackRating)
                        assertNull(ActivityFeedbackStore(directory, Clock.systemUTC()).get(id))
                    }
                }
        }
    }

    @Test
    fun `a failed event without a generated response cannot be rated`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.update { it.copy(paused = false, replyDelaySeconds = 0, showTyping = false) }
        val gateway = FakeTelegramGateway()
        val feedback = ActivityFeedbackStore(directory, Clock.systemUTC())
        ReplyCoordinator(settings, gateway, feedback = feedback) { _, _ -> throw IllegalStateException("test failure") }
            .use { replies ->
                replies.onMessage(gateway.messages.first())
                assertTrue(
                    Stream.generate { replies.activities().firstOrNull()?.status }.limit(200).anyMatch { status ->
                        if (status == "error") {
                            true
                        } else {
                            Thread.sleep(20)
                            false
                        }
                    },
                )
                val entry = replies.activities().single()
                assertFalse(entry.feedbackAvailable)
                assertFailsWith<ActivityFeedbackUnavailableException> {
                    replies.saveFeedback(entry.id, ActivityFeedbackUpdate(ActivityFeedbackRating.BAD))
                }
                assertNull(feedback.get(entry.id))
            }
    }

    @Test
    fun `a ready answer remains available for feedback after Telegram delivery fails`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true))
        settings.update { it.copy(paused = false, replyDelaySeconds = 0, showTyping = false) }
        val gateway = FakeTelegramGateway()
        gateway.nextSendError = IllegalStateException("synthetic delivery failure")
        val feedback = ActivityFeedbackStore(directory, Clock.systemUTC())
        ReplyCoordinator(settings, gateway, feedback = feedback) { _, _ -> ReplyResult("Ready answer", 1, 1.0) }
            .use { replies ->
                replies.onMessage(gateway.messages.first())
                assertTrue(
                    Stream.generate { replies.activities().firstOrNull()?.status }.limit(200).anyMatch { status ->
                        if (status == "error") {
                            true
                        } else {
                            Thread.sleep(20)
                            false
                        }
                    },
                )
                val entry = replies.activities().single()
                assertTrue(entry.feedbackAvailable)
                assertEquals("Ready answer", replies.details(entry.id).response)
                replies.saveFeedback(entry.id, ActivityFeedbackUpdate(ActivityFeedbackRating.GOOD))
                assertEquals(ActivityFeedbackRating.GOOD, feedback.get(entry.id)?.rating)
                assertTrue(gateway.sent.isEmpty())
            }
    }
}
