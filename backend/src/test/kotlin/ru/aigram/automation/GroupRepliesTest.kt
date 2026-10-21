package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupRepliesTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `groups are silent by default even when the chat is enabled`() {
        val settings = preferences()
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(settings, gateway) { _, _ -> error("Unexpected generation") }.use { coordinator ->
            coordinator.onMessage(ChatMessage("7", "42", "Привет", false, 0, group = true, mentionsMe = true))
            assertTrue(coordinator.activities().isEmpty())
            assertTrue(gateway.sent.isEmpty())
        }
    }

    @Test
    fun `mention mode answers only a mention and keeps message and forum topic IDs`() {
        val settings = preferences()
        settings.update { it.copy(groupReplies = GroupReplyMode.MENTIONS) }
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("Ответ", 0, 0.0) }.use { coordinator ->
            coordinator.onMessage(ChatMessage("7", "42", "Чужой разговор", false, 0, group = true))
            assertTrue(gateway.sent.isEmpty())
            coordinator.onMessage(
                ChatMessage("8", "42", "@owner привет", false, 0, group = true, mentionsMe = true, topicId = 31),
            )
            assertTrue(
                Stream.generate {
                    Thread.sleep(20)
                    gateway.sent.isNotEmpty()
                }.limit(200).anyMatch { it },
            )
            assertEquals(listOf("8"), gateway.replyIds)
            assertEquals(listOf(31), gateway.topicIds)
        }
    }

    @Test
    fun `conversation can disable group replies granted globally`() {
        val settings = preferences()
        settings.update { it.copy(groupReplies = GroupReplyMode.ALL) }
        settings.saveRule("42", ChatRule(enabled = true, groupReplies = GroupReplyMode.DISABLED))
        val gateway = FakeTelegramGateway()
        ReplyCoordinator(settings, gateway) { _, _ -> error("Unexpected generation") }.use { coordinator ->
            coordinator.onMessage(ChatMessage("7", "42", "Привет", false, 0, group = true))
            assertTrue(gateway.sent.isEmpty())
        }
    }

    private fun preferences(): SettingsStore = SettingsStore(directory).also { settings ->
        settings.update { it.copy(paused = false, replyDelaySeconds = 0, replyIntervalSeconds = 0, showTyping = false) }
        settings.saveRule("42", ChatRule(enabled = true))
    }
}
