package ru.aigram.ai

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.ai.chat.messages.UserMessage
import ru.aigram.ai.profile.ProfileAiTestServices
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.media.MediaAttachment
import ru.aigram.exception.impl.VisionInputLimitExceededException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val MEBIBYTE = 1024L * 1024

class ReplyImageAlbumTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `all ten photos from the current album are attached chronologically without older or foreign images`() {
        val file = directory.resolve("photo.jpg")
        Files.write(file, byteArrayOf(1, 2, 3))
        val telegram = VisionAlbumGateway((1..10).associateWith { file })
        val photos = (1..10).map { photo(it) }
        val history = listOf(
            photo(20, album = "older"),
            photo(21).copy(outgoing = true),
            photo(22).copy(senderId = "user:other"),
            photo(23).copy(topicId = 2),
        ) + photos.drop(1).take(8) + photos.take(1) + photos.takeLast(1)
        ProfileAiTestServices(directory).use { services ->
            val result = ReplyImageContext(telegram, services.targets).build(ChatRule(analyzePhotos = true), history)
            assertEquals((1..10).map(Int::toString), result.keys.toList())
            assertEquals((1..10).toList(), telegram.downloads)
            assertTrue(result.values.all { it.single().mimeType.toString() == "image/jpeg" })
            val prompt = PromptBuilder().build(ChatRule(contextMessages = 4), photos, images = result)
            assertEquals(
                10,
                prompt.drop(1).count {
                    (it as? UserMessage)?.media?.isNotEmpty() == true
                },
            )
            assertTrue(prompt.drop(1).all { it.messageType.value == "user" })
        }
    }

    @Test
    fun `unrelated later text and disabled photo analysis never attach old images`() {
        val telegram = VisionAlbumGateway(emptyMap())
        val history = listOf(photo(1), ChatMessage("2", "42", "Another question", false, 2))
        ProfileAiTestServices(directory).use { services ->
            val builder = ReplyImageContext(telegram, services.targets)
            assertTrue(builder.build(ChatRule(analyzePhotos = true), history).isEmpty())
            assertTrue(builder.build(ChatRule(analyzePhotos = false), listOf(photo(1))).isEmpty())
            assertTrue(telegram.downloads.isEmpty())
        }
    }

    @Test
    fun `photos without an album id attach only the latest incoming photo`() {
        val file = directory.resolve("photo.jpg")
        Files.write(file, byteArrayOf(1, 2, 3))
        val telegram = VisionAlbumGateway(mapOf(2 to file))
        ProfileAiTestServices(directory).use { services ->
            val result = ReplyImageContext(telegram, services.targets).build(
                ChatRule(analyzePhotos = true),
                listOf(photo(1, album = ""), photo(2, album = "")),
            )
            assertEquals(setOf("2"), result.keys)
            assertEquals(listOf(2), telegram.downloads)
        }
    }

    @Test
    fun `oversized album count per file and combined declared sizes fail before downloading`() {
        val telegram = VisionAlbumGateway(emptyMap())
        val cases = listOf(
            (1..11).map { photo(it) },
            listOf(photo(1, size = 10 * MEBIBYTE + 1)),
            (1..3).map { photo(it, size = 8 * MEBIBYTE) },
        )
        ProfileAiTestServices(directory).use { services ->
            val builder = ReplyImageContext(telegram, services.targets)
            cases.stream().forEach { history ->
                assertFailsWith<VisionInputLimitExceededException> {
                    builder.build(ChatRule(analyzePhotos = true), history)
                }
            }
            assertTrue(telegram.downloads.isEmpty())
        }
    }

    @Test
    fun `actual file sizes enforce per image and combined limits even with missing Telegram size metadata`() {
        val single = directory.resolve("oversized.jpg")
        RandomAccessFile(single.toFile(), "rw").use { it.setLength(10 * MEBIBYTE + 1) }
        val album = directory.resolve("album.jpg")
        RandomAccessFile(album.toFile(), "rw").use { it.setLength(8 * MEBIBYTE) }
        ProfileAiTestServices(directory).use { services ->
            val oversized = VisionAlbumGateway(mapOf(1 to single))
            assertFailsWith<VisionInputLimitExceededException> {
                ReplyImageContext(oversized, services.targets).build(
                    ChatRule(analyzePhotos = true),
                    listOf(photo(1)),
                )
            }
            val combined = VisionAlbumGateway((1..3).associateWith { album })
            assertFailsWith<VisionInputLimitExceededException> {
                ReplyImageContext(combined, services.targets).build(
                    ChatRule(analyzePhotos = true),
                    (1..3).map { photo(it) },
                )
            }
            assertEquals(listOf(1, 2, 3), combined.downloads)
        }
    }

    private fun photo(id: Int, album: String = "777", size: Long = 0): ChatMessage = ChatMessage(
        id.toString(), "42", "", false, id.toLong(), kind = "photo",
        media = MediaAttachment(id, "image/jpeg", "photo.jpg", size = size),
        senderId = "user:10", mediaAlbumId = album,
    )
}
