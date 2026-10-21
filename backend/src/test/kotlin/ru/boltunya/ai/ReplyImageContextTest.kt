package ru.boltunya.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.profile.ModelCapabilities
import ru.boltunya.ai.profile.ModelProfileUpdate
import ru.boltunya.ai.profile.ModelProvider
import ru.boltunya.ai.profile.ProfileAiTestServices
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.MediaAttachment
import ru.boltunya.exception.impl.ModelCapabilityUnavailableException
import ru.boltunya.http.MediaArchiveGateway
import ru.boltunya.memory.MemoryTelegramGateway
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReplyImageContextTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `photo download requires per chat consent and model vision capability`() {
        val photo = directory.resolve("photo.jpg")
        Files.write(photo, byteArrayOf(1, 2, 3))
        val telegram = MediaArchiveGateway(MemoryTelegramGateway(), photo)
        ProfileAiTestServices(directory).use { services ->
            val builder = ReplyImageContext(telegram, services.targets)
            val message =
                ChatMessage(
                    "1",
                    "42",
                    "",
                    false,
                    0,
                    kind = "photo",
                    media = MediaAttachment(7, "image/jpeg", "photo.jpg"),
                )
            assertTrue(builder.build(ChatRule(), listOf(message)).isEmpty())
            assertEquals(0, telegram.downloads)
            assertFailsWith<ModelCapabilityUnavailableException> {
                builder.build(ChatRule(analyzePhotos = true), listOf(message))
            }
            assertEquals(0, telegram.downloads)
            val profile = services.profiles.save(
                null,
                ModelProfileUpdate(
                    "Vision",
                    ModelProvider.OPENAI_COMPATIBLE,
                    baseUrl = "https://example.test/v1",
                    model = "vision",
                    capabilities = ModelCapabilities(vision = true),
                ),
            )
            val images = builder.build(
                ChatRule(analyzePhotos = true, modelProfileId = profile.id, useDefaultModel = false),
                listOf(message),
            )
            assertEquals(setOf("1"), images.keys)
            assertEquals(1, telegram.downloads)
            assertEquals("image/jpeg", images["1"]?.single()?.mimeType.toString())
            assertTrue(
                builder.build(
                    ChatRule(analyzePhotos = true),
                    listOf(message, message.copy(id = "2", text = "Hi", kind = "text", media = null)),
                ).isEmpty(),
            )
        }
    }
}
