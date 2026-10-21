package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.activity.ActivityHistoryStore
import ru.aigram.automation.activity.ReplyTrace
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ActivityKind
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import java.time.Clock
import java.util.UUID
import java.util.stream.Collectors
import kotlin.test.assertEquals

class ReplyCoordinatorAvatarTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `current events and details use the matching live chat photo and respect its removal`() {
        val settings = SettingsStore(directory)
        settings.saveRule("42", ChatRule(enabled = true, delaySeconds = 60))
        settings.update { it.copy(paused = false) }
        val gateway = FakeTelegramGateway()
        gateway.chatSummaries = listOf(chat("42", "/api/media/10"), chat("43", "/api/media/20"))

        ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("Unused", 0, 0.0) }.use { coordinator ->
            coordinator.onMessage(gateway.messages.single())
            val id = coordinator.activities().single().id

            gateway.chatSummaries = listOf(chat("42", "/api/media/30"), chat("43", "/api/media/10"))

            assertEquals("/api/media/30", coordinator.currentActivities().single().avatarUrl)
            assertEquals("/api/media/30", coordinator.details(id).entry.avatarUrl)

            gateway.chatSummaries = listOf(chat("42", ""), chat("43", "/api/media/10"))

            assertEquals("", coordinator.currentActivities().single().avatarUrl)
            assertEquals("", coordinator.details(id).entry.avatarUrl)
        }
    }

    @Test
    fun `restored history never uses an archived media id belonging to another current chat`() {
        val chatEntry = ActivityEntry(
            UUID.randomUUID().toString(),
            "42",
            "Synthetic person",
            "ready",
            "model",
            1000,
            avatarUrl = "/api/media/10",
        )
        val globalEntry = chatEntry.copy(id = UUID.randomUUID().toString(), chatId = "", kind = ActivityKind.MEMORY)
        ActivityHistoryStore(directory, Clock.systemUTC(), ResourceCatalog()).use { history ->
            history.watch("100", ReplyTrace(chatEntry, ""))
            history.watch("100", ReplyTrace(globalEntry, ""))
        }

        val gateway = FakeTelegramGateway()
        gateway.chatSummaries = listOf(chat("42", "/api/media/30"), chat("43", "/api/media/10"))
        ActivityHistoryStore(directory, Clock.systemUTC(), ResourceCatalog()).use { history ->
            ReplyCoordinator(SettingsStore(directory), gateway, history = history) { _, _ ->
                ReplyResult("Unused", 0, 0.0)
            }.use { coordinator ->
                val entries = coordinator.historyPage(null, 30).entries.stream()
                    .collect(Collectors.toMap(ActivityEntry::id) { it })

                assertEquals("/api/media/30", entries.getValue(chatEntry.id).avatarUrl)
                assertEquals("", entries.getValue(globalEntry.id).avatarUrl)
                assertEquals("/api/media/30", coordinator.details(chatEntry.id).entry.avatarUrl)
                assertEquals("", coordinator.details(globalEntry.id).entry.avatarUrl)

                gateway.chatSummaries = listOf(chat("43", "/api/media/10"))

                assertEquals("", coordinator.historyPage(null, 30).entries.single { it.id == chatEntry.id }.avatarUrl)
                assertEquals("", coordinator.details(chatEntry.id).entry.avatarUrl)
            }
        }
    }

    private fun chat(id: String, avatarUrl: String): ChatSummary = ChatSummary(
        id,
        "Synthetic person $id",
        "",
        0,
        0,
        ChatRule(),
        avatarUrl = avatarUrl,
    )
}
