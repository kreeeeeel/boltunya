package ru.boltunya.domain

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import ru.boltunya.validation.ResourcePattern

@Serializable
data class ChatRule(
    val enabled: Boolean = false,
    val groupReplies: GroupReplyMode = GroupReplyMode.INHERIT,
    @field:ResourcePattern("common/communication-style")
    val style: String = "friendly",
    @field:Size(max = 64)
    val styleId: String = "",
    @field:Size(max = 120)
    val model: String = "qwen3:1.7b",
    val useDefaultModel: Boolean = true,
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
    val internetAccess: Boolean = true,
    val sourceLinks: SourceLinks = SourceLinks.REQUESTED,
    val matchMyStyle: Boolean = true,
    val useMemory: Boolean = true,
)
