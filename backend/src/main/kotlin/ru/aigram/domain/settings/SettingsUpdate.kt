package ru.aigram.domain.settings

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import ru.aigram.domain.chat.DeliveryMode
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.domain.chat.WritingStyle
import ru.aigram.domain.memory.MemoryCollectionScope
import ru.aigram.domain.speech.SpeechComputeDevice

@Serializable
data class SettingsUpdate(
    val paused: Boolean? = null,
    @field:Min(1) @field:Max(4) val maxParallelTasks: Int? = null,
    val defaultModel: String? = null,
    @field:Size(max = 36) val defaultModelProfileId: String? = null,
    val deliveryMode: DeliveryMode? = null,
    @field:Size(max = 80)
    val ollamaDevice: String? = null,
    @field:Size(max = 4000)
    val globalPrompt: String? = null,
    @field:Min(0) @field:Max(120)
    val replyDelaySeconds: Int? = null,
    @field:Min(0) @field:Max(120)
    val replyIntervalSeconds: Int? = null,
    val showTyping: Boolean? = null,
    val formatReplies: Boolean? = null,
    val voiceInputEnabled: Boolean? = null,
    val voiceConversionDevice: SpeechComputeDevice? = null,
    val voiceReplyMode: VoiceReplyMode? = null,
    @field:Min(0) @field:Max(100) val voiceReplyProbability: Int? = null,
    @field:Size(max = 80) val voiceRecognitionModel: String? = null,
    @field:Size(max = 80) val voiceSynthesisModel: String? = null,
    @field:DecimalMin("0.5") @field:DecimalMax("2.0") val voiceSpeechRate: Double? = null,
    val replyProcessingMode: ReplyProcessingMode? = null,
    val ignoreAssistantTasks: Boolean? = null,
    val memoryEnabled: Boolean? = null,
    val memoryCollectionScope: MemoryCollectionScope? = null,
    val proactiveEnabled: Boolean? = null,
    @field:Min(5) @field:Max(43200) val proactiveIntervalMinutes: Int? = null,
    @field:Min(1) @field:Max(168)
    val memoryIntervalHours: Int? = null,
    val autoDownloadPhotos: Boolean? = null,
    val autoDownloadVideos: Boolean? = null,
    val autoDownloadAudio: Boolean? = null,
    val autoDownloadFiles: Boolean? = null,
    @field:Min(1) @field:Max(100)
    val autoDownloadMaxMb: Int? = null,
    val groupReplies: GroupReplyMode? = null,
    @field:Valid @field:Size(max = 30)
    val customStyles: List<WritingStyle>? = null,
    val closeToTray: Boolean? = null,
    val launchAtLogin: Boolean? = null,
    val resumeOnStartup: Boolean? = null,
)
