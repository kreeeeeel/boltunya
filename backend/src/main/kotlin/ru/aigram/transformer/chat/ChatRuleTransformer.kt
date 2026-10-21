package ru.aigram.transformer.chat

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.ChatGroupReplies
import ru.aigram.contract.dto.ChatRuleInput
import ru.aigram.contract.dto.SourceLinksMode
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.DeliveryMode
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.domain.chat.ProactiveMode
import ru.aigram.domain.chat.SourceLinks
import ru.aigram.contract.dto.ChatRule as ContractChatRule
import ru.aigram.contract.dto.CommunicationStyle as ContractCommunicationStyle
import ru.aigram.contract.dto.DeliveryMode as ContractDeliveryMode
import ru.aigram.contract.dto.ProactiveMode as ContractProactiveMode

@Component
class ChatRuleTransformer {
    fun toContract(value: ChatRule): ContractChatRule = ContractChatRule(
        enabled = value.enabled,
        style = ContractCommunicationStyle.forValue(value.style),
        model = value.model,
        instructions = value.instructions,
        contextMessages = value.contextMessages,
        maxTokens = value.maxTokens,
        delaySeconds = value.delaySeconds,
        creativity = value.creativity,
        emoji = value.emoji,
        modelProfileId = value.modelProfileId,
        deliveryMode = ContractDeliveryMode.valueOf(value.deliveryMode.name),
        analyzePhotos = value.analyzePhotos,
        aiStickers = value.aiStickers,
        internetAccess = value.internetAccess,
        matchMyStyle = value.matchMyStyle,
        styleId = value.styleId,
        groupReplies = ChatGroupReplies.valueOf(value.groupReplies.name),
        useMemory = value.useMemory,
        sourceLinks = SourceLinksMode.valueOf(value.sourceLinks.name),
        useDefaultModel = value.useDefaultModel,
        proactiveMode = ContractProactiveMode.valueOf(value.proactiveMode.name),
        proactiveIntervalMinutes = value.proactiveIntervalMinutes,
    )

    fun toDomain(value: ChatRuleInput): ChatRule = ChatRule(
        enabled = value.enabled,
        style = value.style.value,
        model = value.model,
        instructions = value.instructions,
        contextMessages = value.contextMessages,
        maxTokens = value.maxTokens,
        delaySeconds = value.delaySeconds,
        creativity = value.creativity,
        emoji = value.emoji,
        modelProfileId = value.modelProfileId,
        deliveryMode = DeliveryMode.valueOf(value.deliveryMode.name),
        analyzePhotos = value.analyzePhotos,
        aiStickers = value.aiStickers,
        internetAccess = value.internetAccess,
        matchMyStyle = value.matchMyStyle,
        styleId = value.styleId,
        groupReplies = GroupReplyMode.valueOf(value.groupReplies.name),
        useMemory = value.useMemory,
        sourceLinks = SourceLinks.valueOf(value.sourceLinks.name),
        useDefaultModel = value.useDefaultModel,
        proactiveMode = ProactiveMode.valueOf(value.proactiveMode.name),
        proactiveIntervalMinutes = value.proactiveIntervalMinutes,
    )
}
