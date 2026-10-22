package ru.aigram.transformer.chat

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.ChatGroupReplies
import ru.aigram.contract.dto.ChatRuleInput
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.DeliveryMode
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.domain.chat.ProactiveMessageMode
import ru.aigram.domain.chat.ProactiveMode
import ru.aigram.domain.chat.ReplyInterruptionMode
import ru.aigram.domain.chat.VoiceSettingsMode
import ru.aigram.domain.settings.VoiceReplyMode
import java.util.Arrays
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import ru.aigram.contract.dto.ProactiveMessageMode as ContractProactiveMessageMode
import ru.aigram.contract.dto.ProactiveMode as ContractProactiveMode
import ru.aigram.contract.dto.ReplyInterruptionMode as ContractReplyInterruptionMode
import ru.aigram.contract.dto.VoiceReplyMode as ContractVoiceReplyMode
import ru.aigram.contract.dto.VoiceSettingsMode as ContractVoiceSettingsMode

class ChatRuleTransformerTest {

    @Test
    fun `retains every editable chat rule field across the generated contract`() {
        val rule = ChatRule(
            enabled = true,
            replyInterruptionMode = ReplyInterruptionMode.EACH,
            proactiveMode = ProactiveMode.ENABLED,
            proactiveIntervalMinutes = 123,
            proactiveMessageMode = ProactiveMessageMode.TEXT,
            proactiveText = "Как дела?",
            groupReplies = GroupReplyMode.MENTIONS,
            style = "rough",
            styleId = "preset-1",
            model = "example:model",
            useDefaultModel = false,
            modelProfileId = "profile-1",
            deliveryMode = DeliveryMode.SEND,
            analyzePhotos = true,
            aiStickers = true,
            voiceSettingsMode = VoiceSettingsMode.OVERRIDE,
            voiceInputEnabled = true,
            voiceReplyMode = VoiceReplyMode.RANDOM,
            voiceReplyProbability = 34,
            voiceRecognitionModel = "whisper-tiny",
            voiceSynthesisModel = "piper-en-amy",
            voiceSpeechRate = 1.3,
            instructions = "Reply concisely",
            contextMessages = 42,
            maxTokens = 256,
            delaySeconds = 17,
            creativity = 37,
            emoji = false,
            matchMyStyle = false,
            useMemory = false,
        )

        val response = rule.toContract()
        val input = ChatRuleInput(
            enabled = response.enabled,
            replyInterruptionMode = ContractReplyInterruptionMode.EACH,
            style = response.style,
            model = response.model,
            instructions = response.instructions,
            contextMessages = response.contextMessages,
            maxTokens = response.maxTokens,
            delaySeconds = response.delaySeconds,
            creativity = response.creativity,
            emoji = response.emoji,
            matchMyStyle = false,
            styleId = "preset-1",
            groupReplies = ChatGroupReplies.MENTIONS,
            useMemory = false,
            useDefaultModel = false,
            modelProfileId = response.modelProfileId,
            deliveryMode = response.deliveryMode,
            analyzePhotos = response.analyzePhotos,
            aiStickers = response.aiStickers,
            voiceSettingsMode = ContractVoiceSettingsMode.OVERRIDE,
            voiceInputEnabled = true,
            voiceReplyMode = ContractVoiceReplyMode.RANDOM,
            voiceReplyProbability = 34,
            voiceRecognitionModel = "whisper-tiny",
            voiceSynthesisModel = "piper-en-amy",
            voiceSpeechRate = 1.3,
            proactiveMode = ContractProactiveMode.ENABLED,
            proactiveIntervalMinutes = 123,
            proactiveMessageMode = ContractProactiveMessageMode.TEXT,
            proactiveText = "Как дела?",
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
