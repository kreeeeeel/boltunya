package ru.aigram.domain.settings

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.DeliveryMode
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.domain.chat.WritingStyle
import ru.aigram.domain.memory.MemoryCollectionScope

@Serializable
data class AppSettings(
    val paused: Boolean = true,
    @field:Min(1) @field:Max(4) val maxParallelTasks: Int = 1,
    val defaultModel: String = "gemma3:4b",
    @field:Size(max = 36) val defaultModelProfileId: String = "",
    val deliveryMode: DeliveryMode = DeliveryMode.REPLY,
    @field:Size(max = 80)
    val ollamaDevice: String = "auto",
    val globalPrompt: String = "",
    @field:Min(0)
    @field:Max(120)
    val replyDelaySeconds: Int = 3,
    @field:Min(0)
    @field:Max(120)
    val replyIntervalSeconds: Int = 3,
    val showTyping: Boolean = true,
    val ignoreAssistantTasks: Boolean = true,
    val memoryEnabled: Boolean = true,
    val memoryCollectionScope: MemoryCollectionScope = MemoryCollectionScope.AI_ENABLED,
    val proactiveEnabled: Boolean = false,
    @field:Min(5) @field:Max(43200) val proactiveIntervalMinutes: Int = 60,
    @field:Min(1) @field:Max(168)
    val memoryIntervalHours: Int = 6,
    val autoDownloadPhotos: Boolean = true,
    val autoDownloadVideos: Boolean = false,
    val autoDownloadAudio: Boolean = false,
    val autoDownloadFiles: Boolean = false,
    @field:Min(1) @field:Max(100)
    val autoDownloadMaxMb: Int = 20,
    val groupReplies: GroupReplyMode = GroupReplyMode.DISABLED,
    @field:Valid @field:Size(max = 30)
    val customStyles: List<WritingStyle> = emptyList(),
    val closeToTray: Boolean = true,
    val launchAtLogin: Boolean = false,
    val resumeOnStartup: Boolean = false,
    val rules: Map<String, ChatRule> = emptyMap(),
)
