package ru.aigram.domain.ai.prompt

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable
import ru.aigram.domain.chat.ChatRule

@Serializable
data class PromptBudgetRequest(
    @field:Valid val rule: ChatRule = ChatRule(),
    @field:Size(max = 4000) val globalPrompt: String? = null,
    @field:Valid val preferences: ModelPromptSettings? = null,
)
