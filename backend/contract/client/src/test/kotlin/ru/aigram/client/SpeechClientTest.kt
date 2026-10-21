package ru.aigram.client

import org.junit.jupiter.api.Test
import ru.aigram.client.configuration.AigramClientFactory
import ru.aigram.client.generated.infrastructure.Serializer
import ru.aigram.client.generated.model.PreviewJobStatus
import ru.aigram.client.generated.model.VoiceDraftRequest
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpeechClientTest {
    @Test
    fun `typed voice drafts preserve legacy omission and per-request model and rate without settings calls`() {
        ClientTestServer().start().use { server ->
            server.response(
                "/api/speech/drafts",
                """{"id":"synthetic-job","status":"queued","result":null,"error":""}""",
            )
            AigramClientFactory().create(server.baseUri, { "synthetic-session" }).use { client ->
                val legacy = client.speech.enqueueVoiceDraft(VoiceDraftRequest("42", "Legacy exact text"))
                    .block(Duration.ofSeconds(5))
                assertEquals("synthetic-job", assertNotNull(legacy).id)
                val explicit = client.speech.enqueueVoiceDraft(
                    VoiceDraftRequest("42", "  Exact voice text.  ", modelId = "piper-en-amy", speed = 1.5),
                ).block(Duration.ofSeconds(5))
                assertEquals(PreviewJobStatus.QUEUED, assertNotNull(explicit).status)
            }

            val mapper = Serializer.jacksonObjectMapper
            val legacyBody = mapper.readTree(server.requests[0].body)
            assertEquals("42", legacyBody.get("chatId").asText())
            assertFalse(legacyBody.has("modelId"))
            assertFalse(legacyBody.has("speed"))
            val explicitBody = mapper.readTree(server.requests[1].body)
            assertEquals("  Exact voice text.  ", explicitBody.get("text").asText())
            assertEquals("piper-en-amy", explicitBody.get("modelId").asText())
            assertEquals(1.5, explicitBody.get("speed").asDouble())
            assertTrue(server.requests.all { it.path == "/api/speech/drafts" && it.method == "POST" })
            assertTrue(server.requests.all { it.cookie == "tiho=synthetic-session" })
        }
    }
}
