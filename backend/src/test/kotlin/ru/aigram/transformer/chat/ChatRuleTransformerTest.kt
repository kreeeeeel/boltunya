package ru.aigram.transformer.chat

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.ChatGroupReplies
import ru.aigram.contract.dto.ChatRuleInput
import ru.aigram.contract.dto.SourceLinksMode
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.DeliveryMode
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.domain.chat.ProactiveMode
import ru.aigram.domain.chat.SourceLinks
import java.util.Arrays
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import ru.aigram.contract.dto.ProactiveMode as ContractProactiveMode

class ChatRuleTransformerTest {

    @Test
    fun `retains every editable chat rule field across the generated contract`() {
        val rule = ChatRule(
            enabled = true,
            proactiveMode = ProactiveMode.ENABLED,
            proactiveIntervalMinutes = 123,
            groupReplies = GroupReplyMode.MENTIONS,
            style = "rough",
            styleId = "preset-1",
            model = "example:model",
            useDefaultModel = false,
            modelProfileId = "profile-1",
            deliveryMode = DeliveryMode.SEND,
            analyzePhotos = true,
            aiStickers = true,
            instructions = "Reply concisely",
            contextMessages = 42,
            maxTokens = 256,
            delaySeconds = 17,
            creativity = 37,
            emoji = false,
            internetAccess = false,
            sourceLinks = SourceLinks.NEVER,
            matchMyStyle = false,
            useMemory = false,
        )

        val response = rule.toContract()
        val input = ChatRuleInput(
            enabled = response.enabled,
            style = response.style,
            model = response.model,
            instructions = response.instructions,
            contextMessages = response.contextMessages,
            maxTokens = response.maxTokens,
            delaySeconds = response.delaySeconds,
            creativity = response.creativity,
            emoji = response.emoji,
            internetAccess = false,
            matchMyStyle = false,
            styleId = "preset-1",
            groupReplies = ChatGroupReplies.MENTIONS,
            useMemory = false,
            sourceLinks = SourceLinksMode.NEVER,
            useDefaultModel = false,
            modelProfileId = response.modelProfileId,
            deliveryMode = response.deliveryMode,
            analyzePhotos = response.analyzePhotos,
            aiStickers = response.aiStickers,
            proactiveMode = ContractProactiveMode.ENABLED,
            proactiveIntervalMinutes = 123,
        )

        assertEquals(rule, input.toDomain())
    }

    @Test
    fun `omitted optional contract fields retain existing chat defaults`() {
        assertEquals(ChatRule(), ChatRuleInput().toDomain())
    }

    @Test
    fun `does not expose resolved internal style instructions`() {
        val rule = ChatRule(resolvedStyleInstructions = "Internal resolved preset")

        val response = rule.toContract()

        assertFalse(
            Arrays.stream(response.javaClass.declaredFields).anyMatch {
                it.name == "resolvedStyleInstructions"
            },
        )
    }
}
