package ru.aigram.transformer.chat

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

fun ChatRule.toContract(): ContractChatRule = ContractChatRule(
    enabled = enabled,
    style = ContractCommunicationStyle.forValue(style),
    model = model,
    instructions = instructions,
    contextMessages = contextMessages,
    maxTokens = maxTokens,
    delaySeconds = delaySeconds,
    creativity = creativity,
    emoji = emoji,
    modelProfileId = modelProfileId,
    deliveryMode = ContractDeliveryMode.valueOf(deliveryMode.name),
    analyzePhotos = analyzePhotos,
    aiStickers = aiStickers,
    internetAccess = internetAccess,
    matchMyStyle = matchMyStyle,
    styleId = styleId,
    groupReplies = ChatGroupReplies.valueOf(groupReplies.name),
    useMemory = useMemory,
    sourceLinks = SourceLinksMode.valueOf(sourceLinks.name),
    useDefaultModel = useDefaultModel,
    proactiveMode = ContractProactiveMode.valueOf(proactiveMode.name),
    proactiveIntervalMinutes = proactiveIntervalMinutes,
)

fun ChatRuleInput.toDomain(): ChatRule = ChatRule(
    enabled = enabled,
    style = style.value,
    model = model,
    instructions = instructions,
    contextMessages = contextMessages,
    maxTokens = maxTokens,
    delaySeconds = delaySeconds,
    creativity = creativity,
    emoji = emoji,
    modelProfileId = modelProfileId,
    deliveryMode = DeliveryMode.valueOf(deliveryMode.name),
    analyzePhotos = analyzePhotos,
    aiStickers = aiStickers,
    internetAccess = internetAccess,
    matchMyStyle = matchMyStyle,
    styleId = styleId,
    groupReplies = GroupReplyMode.valueOf(groupReplies.name),
    useMemory = useMemory,
    sourceLinks = SourceLinks.valueOf(sourceLinks.name),
    useDefaultModel = useDefaultModel,
    proactiveMode = ProactiveMode.valueOf(proactiveMode.name),
    proactiveIntervalMinutes = proactiveIntervalMinutes,
)
