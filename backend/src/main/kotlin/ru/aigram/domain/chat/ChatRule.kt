package ru.aigram.domain.chat

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.aigram.domain.settings.VoiceReplyMode
import ru.aigram.validation.InputFormat
import ru.aigram.validation.InputRule

@Serializable
data class ChatRule(
    val enabled: Boolean = false,
    val replyInterruptionMode: ReplyInterruptionMode = ReplyInterruptionMode.INHERIT,
    val proactiveMode: ProactiveMode = ProactiveMode.INHERIT,
    @field:Min(0) @field:Max(43200) val proactiveIntervalMinutes: Int = 0,
    val proactiveMessageMode: ProactiveMessageMode = ProactiveMessageMode.AI,
    @field:Size(max = 4096) val proactiveText: String = "",
    val groupReplies: GroupReplyMode = GroupReplyMode.INHERIT,
    @field:InputFormat(InputRule.COMMUNICATION_STYLE)
    val style: String = "friendly",
    @field:Size(max = 64)
    val styleId: String = "",
    @field:Size(max = 120)
    val model: String = "qwen3:1.7b",
    val useDefaultModel: Boolean = true,
    @field:Size(max = 36) val modelProfileId: String = "",
    val deliveryMode: DeliveryMode = DeliveryMode.INHERIT,
    val analyzePhotos: Boolean = false,
    val aiStickers: Boolean = false,
    val voiceSettingsMode: VoiceSettingsMode = VoiceSettingsMode.INHERIT,
    val voiceInputEnabled: Boolean = false,
    val voiceReplyMode: VoiceReplyMode = VoiceReplyMode.TEXT,
    @field:Min(0) @field:Max(100) val voiceReplyProbability: Int = 50,
    @field:NotBlank @field:Size(max = 80) val voiceRecognitionModel: String = "whisper-tiny",
    @field:NotBlank @field:Size(max = 80) val voiceSynthesisModel: String = "piper-ru-irina",
    @field:DecimalMin("0.5") @field:DecimalMax("2.0") val voiceSpeechRate: Double = 1.0,
    @field:Size(max = 4000)
    val instructions: String = "",
    @field:Min(4)
    @field:Max(60)
    val contextMessages: Int = 20,
    @field:Min(32)
    @field:Max(512)
    val maxTokens: Int = 128,
    @field:Min(0)
    @field:Max(60)
    val delaySeconds: Int = 0,
    @field:Min(0)
    @field:Max(100)
    val creativity: Int = 60,
    val emoji: Boolean = true,
    val matchMyStyle: Boolean = true,
    val useMemory: Boolean = true,
    @Transient
    val resolvedStyleInstructions: String = "",
)
