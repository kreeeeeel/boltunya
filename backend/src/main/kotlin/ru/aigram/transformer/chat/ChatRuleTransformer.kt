package ru.aigram.transformer.chat

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
import ru.aigram.contract.dto.ChatRule as ContractChatRule
import ru.aigram.contract.dto.CommunicationStyle as ContractCommunicationStyle
import ru.aigram.contract.dto.DeliveryMode as ContractDeliveryMode
import ru.aigram.contract.dto.ProactiveMessageMode as ContractProactiveMessageMode
import ru.aigram.contract.dto.ProactiveMode as ContractProactiveMode
import ru.aigram.contract.dto.ReplyInterruptionMode as ContractReplyInterruptionMode
import ru.aigram.contract.dto.VoiceReplyMode as ContractVoiceReplyMode
import ru.aigram.contract.dto.VoiceSettingsMode as ContractVoiceSettingsMode

fun ChatRule.toContract(): ContractChatRule = ContractChatRule(
    enabled = enabled,
    replyInterruptionMode = ContractReplyInterruptionMode.valueOf(replyInterruptionMode.name),
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
    voiceSettingsMode = ContractVoiceSettingsMode.valueOf(voiceSettingsMode.name),
    voiceInputEnabled = voiceInputEnabled,
    voiceReplyMode = ContractVoiceReplyMode.valueOf(voiceReplyMode.name),
    voiceReplyProbability = voiceReplyProbability,
    voiceRecognitionModel = voiceRecognitionModel,
    voiceSynthesisModel = voiceSynthesisModel,
    voiceSpeechRate = voiceSpeechRate,
    matchMyStyle = matchMyStyle,
    styleId = styleId,
    groupReplies = ChatGroupReplies.valueOf(groupReplies.name),
    useMemory = useMemory,
    useDefaultModel = useDefaultModel,
    proactiveMode = ContractProactiveMode.valueOf(proactiveMode.name),
    proactiveIntervalMinutes = proactiveIntervalMinutes,
    proactiveMessageMode = ContractProactiveMessageMode.valueOf(proactiveMessageMode.name),
    proactiveText = proactiveText,
)

fun ChatRuleInput.toDomain(): ChatRule = ChatRule(
    enabled = enabled,
    replyInterruptionMode = ReplyInterruptionMode.valueOf(replyInterruptionMode.name),
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
    voiceSettingsMode = VoiceSettingsMode.valueOf(voiceSettingsMode.name),
    voiceInputEnabled = voiceInputEnabled,
    voiceReplyMode = VoiceReplyMode.valueOf(voiceReplyMode?.name ?: VoiceReplyMode.TEXT.name),
    voiceReplyProbability = voiceReplyProbability,
    voiceRecognitionModel = voiceRecognitionModel,
    voiceSynthesisModel = voiceSynthesisModel,
    voiceSpeechRate = voiceSpeechRate,
    matchMyStyle = matchMyStyle,
    styleId = styleId,
    groupReplies = GroupReplyMode.valueOf(groupReplies.name),
    useMemory = useMemory,
    useDefaultModel = useDefaultModel,
    proactiveMode = ProactiveMode.valueOf(proactiveMode.name),
    proactiveIntervalMinutes = proactiveIntervalMinutes,
    proactiveMessageMode = ProactiveMessageMode.valueOf(proactiveMessageMode.name),
    proactiveText = proactiveText,
)
