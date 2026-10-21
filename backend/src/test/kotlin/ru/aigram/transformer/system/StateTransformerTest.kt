package ru.aigram.transformer.system

import org.junit.jupiter.api.Test
import ru.aigram.domain.ai.EngineStatus
import ru.aigram.domain.ai.model.ModelInfo
import ru.aigram.domain.ai.model.ModelMeasurement
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ReplyQueueState
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.system.ApiState
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.transformer.activity.ActivityTransformer
import ru.aigram.transformer.chat.ChatRuleTransformer
import ru.aigram.transformer.chat.ChatTransformer
import ru.aigram.transformer.chat.MessageTransformer
import ru.aigram.transformer.media.MediaTransformer
import ru.aigram.transformer.settings.SettingsTransformer
import ru.aigram.transformer.telegram.TelegramTransformer
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StateTransformerTest {
    @Test
    fun `state composes real feature DTOs and preserves active queue engine progress and large identifiers`() {
        val rules = ChatRuleTransformer()
        val transformer = StateTransformer(
            TelegramTransformer(),
            EngineStateTransformer(),
            SettingsTransformer(rules),
            ChatTransformer(rules, MessageTransformer(MediaTransformer())),
            ActivityTransformer(),
        )
        val chatId = "-1009007199254740993"
        val model = ModelInfo(
            "qwen3-vl:4b",
            "Qwen Vision",
            "Synthetic description",
            "3.3 GB",
            installed = true,
            supportsVision = true,
            measurement = ModelMeasurement(3_000_000_000L, 8.25),
        )
        val value = ApiState(
            TelegramStatus("ready", name = "Synthetic owner", userId = "9007199254740993"),
            EngineStatus(
                online = true,
                models = listOf(model),
                downloading = "gemma3:4b",
                progress = 37,
                warming = "qwen3-vl:4b",
                downloadedBytes = 4_000_000_000L,
                totalBytes = 8_000_000_000L,
                installed = true,
            ),
            AppSettings(),
            listOf(ChatSummary(chatId, "Synthetic chat", "Hello", 4_000_000_000L, 1, ChatRule())),
            listOf(ActivityEntry("event-1", chatId, "Synthetic chat", "queued", "", 4_000_000_000L)),
            ReplyQueueState(paused = true, queued = 3, running = 2, maxParallelTasks = 4),
        )

        val mapped = transformer.toContract(value)

        assertEquals(value.telegram.userId, mapped.telegram.userId)
        assertEquals(chatId, mapped.chats.single().id)
        assertEquals(4_000_000_000L, mapped.chats.single().date)
        assertEquals(chatId, mapped.activity.single().chatId)
        assertEquals(37, mapped.engine.progress)
        assertEquals(4_000_000_000L, mapped.engine.downloadedBytes)
        assertEquals("qwen3-vl:4b", mapped.engine.warming)
        assertTrue(mapped.engine.models.single().supportsVision == true)
        assertEquals(BigDecimal("8.25"), mapped.engine.models.single().measurement?.tokensPerSecond)
        assertEquals(3_000_000_000L, mapped.engine.models.single().measurement?.durationMs)
        assertEquals(3L, mapped.replyQueue?.queued)
        assertEquals(2, mapped.replyQueue?.activeTasks)
        assertEquals(4, mapped.replyQueue?.maxParallelTasks)
    }
}
