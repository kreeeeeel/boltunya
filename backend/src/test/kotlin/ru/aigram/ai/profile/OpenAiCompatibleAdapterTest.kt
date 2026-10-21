package ru.aigram.ai.profile

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeTypeUtils
import ru.aigram.automation.activity.ReplyTrace
import ru.aigram.domain.ActivityEntry
import ru.aigram.domain.ChatRule
import ru.aigram.exception.impl.ModelProviderRefusedException
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAiCompatibleAdapterTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `non streaming compatible reasoning alias is separated from final content`() {
        val response = """
            {
              "id":"test","object":"chat.completion","created":1,"model":"stub",
              "choices":[{"index":0,"message":{"role":"assistant","content":"Final answer",
              "reasoning":"Private provider analysis"},"finish_reason":"stop"}],
              "usage":{"prompt_tokens":4,"completion_tokens":2,"total_tokens":6}
            }
        """.trimIndent()
        OpenAiStubServer(response, false).start().use { server ->
            ProfileAiTestServices(directory).use { services ->
                val profile = services.profiles.save(
                    null,
                    ModelProfileUpdate(
                        "Synthetic",
                        ModelProvider.OPENAI_COMPATIBLE,
                        baseUrl = server.baseUrl,
                        model = "stub",
                        capabilities = ModelCapabilities(streaming = false),
                    ),
                )
                val result = services.completion.generate(
                    ChatRule(modelProfileId = profile.id, useDefaultModel = false),
                    listOf(UserMessage("Synthetic")),
                )
                assertEquals("Final answer", result.text)
                assertEquals("Private provider analysis", result.thinking)
                assertEquals(1, server.requests.get())
            }
        }
    }

    @Test
    fun `streams reasoning separately and reads final usage without duplicating cumulative reasoning`() {
        val stream = """
            data: {"id":"test","object":"chat.completion.chunk","created":1,"model":"stub","choices":[{"index":0,"delta":{"role":"assistant","reasoning_content":"Think "},"finish_reason":null}]}

            data: {"id":"test","object":"chat.completion.chunk","created":1,"model":"stub","choices":[{"index":0,"delta":{"reasoning_content":"carefully"},"finish_reason":null}]}

            data: {"id":"test","object":"chat.completion.chunk","created":1,"model":"stub","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}

            data: {"id":"test","object":"chat.completion.chunk","created":1,"model":"stub","choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":7,"completion_tokens":3,"total_tokens":10}}

            data: [DONE]

        """.trimIndent()
        OpenAiStubServer(stream).start().use { server ->
            ProfileAiTestServices(directory).use { services ->
                val profile = services.profiles.save(
                    null,
                    ModelProfileUpdate(
                        "Stub",
                        ModelProvider.OPENAI_COMPATIBLE,
                        baseUrl = server.baseUrl,
                        model = "stub",
                        apiKey = "stub-secret",
                    ),
                )
                val trace = ReplyTrace(ActivityEntry("test", "42", "Test", "generating", "", 0), "Hello")
                val result = services.tracing.capture(trace) {
                    services.completion.generate(
                        ChatRule(modelProfileId = profile.id, useDefaultModel = false),
                        listOf(UserMessage("Hello")),
                    )
                }
                assertEquals("Hello", result.text)
                assertEquals("Think carefully", result.thinking)
                assertEquals(7, result.inputTokens)
                assertEquals(3, result.outputTokens)
                assertEquals("Bearer stub-secret", server.authorization.get())
                assertEquals(1, server.requests.get())
                assertFalse(server.body.get().containsKey("temperature"))
                assertFalse(server.body.get().containsKey("num_ctx"))
                assertFalse(trace.details().attempts.single().options.contains("stub-secret"))
                assertTrue(trace.details().attempts.single().options.contains(profile.id))
            }
        }
    }

    @Test
    fun `structured refusal is terminal and never retried`() {
        val stream = """
            data: {"id":"test","object":"chat.completion.chunk","created":1,"model":"stub","choices":[{"index":0,"delta":{"role":"assistant","refusal":"Request declined"},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent()
        OpenAiStubServer(stream).start().use { server ->
            ProfileAiTestServices(directory).use { services ->
                val profile = services.profiles.save(
                    null,
                    ModelProfileUpdate(
                        "Stub",
                        ModelProvider.OPENAI_COMPATIBLE,
                        baseUrl = server.baseUrl,
                        model = "stub",
                    ),
                )
                assertFailsWith<ModelProviderRefusedException> {
                    services.completion.generate(
                        ChatRule(modelProfileId = profile.id, useDefaultModel = false),
                        listOf(UserMessage("Test")),
                    )
                }
                assertEquals(1, server.requests.get())
                assertEquals("", server.authorization.get())
            }
        }
    }

    @Test
    fun `non streaming vision sends image bytes with correct roles rather than an inaccessible local URL`() {
        val response = """
            {
              "id":"test","object":"chat.completion","created":1,"model":"stub",
              "choices":[{"index":0,"message":{"role":"assistant","content":"A photo"},"finish_reason":"stop"}],
              "usage":{"prompt_tokens":4,"completion_tokens":2,"total_tokens":6}
            }
        """.trimIndent()
        OpenAiStubServer(response, false).start().use { server ->
            ProfileAiTestServices(directory).use { services ->
                val profile = services.profiles.save(
                    null,
                    ModelProfileUpdate(
                        "Vision",
                        ModelProvider.OPENAI_COMPATIBLE,
                        baseUrl = server.baseUrl,
                        model = "stub",
                        capabilities = ModelCapabilities(streaming = false, vision = true),
                    ),
                )
                val media = Media(MimeTypeUtils.IMAGE_PNG, ByteArrayResource(byteArrayOf(1, 2, 3)))
                val message = UserMessage.builder().text("Describe").media(media).build()
                val result = services.completion.generate(
                    ChatRule(modelProfileId = profile.id, useDefaultModel = false),
                    listOf(message),
                    task = ModelTask.VISION,
                )
                assertEquals("A photo", result.text)
                val sent = assertNotNull(server.body.get()["messages"]).jsonArray.first().jsonObject
                assertEquals("user", sent["role"]?.jsonPrimitive?.content)
                assertTrue(sent["content"].toString().contains("data:image/png;base64,AQID"))
            }
        }
    }
}
