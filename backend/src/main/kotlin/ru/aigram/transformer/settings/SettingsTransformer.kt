package ru.aigram.transformer.settings

import ru.aigram.contract.dto.Settings
import ru.aigram.contract.dto.WritingStyle
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.settings.SettingsUpdate
import ru.aigram.transformer.chat.toContract
import java.util.stream.Collectors
import ru.aigram.contract.dto.DefaultDeliveryMode as ContractDefaultDeliveryMode
import ru.aigram.contract.dto.GroupRepliesMode as ContractGroupRepliesMode
import ru.aigram.contract.dto.MemoryCollectionScope as ContractMemoryCollectionScope
import ru.aigram.contract.dto.ReplyProcessingMode as ContractReplyProcessingMode
import ru.aigram.contract.dto.SettingsUpdate as ContractSettingsUpdate
import ru.aigram.domain.chat.DeliveryMode as DomainDeliveryMode
import ru.aigram.domain.chat.GroupReplyMode as DomainGroupReplyMode
import ru.aigram.domain.chat.WritingStyle as DomainWritingStyle
import ru.aigram.domain.memory.MemoryCollectionScope as DomainMemoryCollectionScope
import ru.aigram.domain.settings.ReplyProcessingMode as DomainReplyProcessingMode

fun AppSettings.toContract(): Settings = Settings(
    paused = paused,
    maxParallelTasks = maxParallelTasks,
    defaultModel = defaultModel,
    defaultModelProfileId = defaultModelProfileId,
    deliveryMode = ContractDefaultDeliveryMode.valueOf(deliveryMode.name),
    ollamaDevice = ollamaDevice,
    globalPrompt = globalPrompt,
    replyDelaySeconds = replyDelaySeconds,
    replyIntervalSeconds = replyIntervalSeconds,
    showTyping = showTyping,
    formatReplies = formatReplies,
    replyProcessingMode = ContractReplyProcessingMode.valueOf(replyProcessingMode.name),
    ignoreAssistantTasks = ignoreAssistantTasks,
    memoryEnabled = memoryEnabled,
    memoryCollectionScope = ContractMemoryCollectionScope.valueOf(memoryCollectionScope.name),
    proactiveEnabled = proactiveEnabled,
    proactiveIntervalMinutes = proactiveIntervalMinutes,
    memoryIntervalHours = memoryIntervalHours,
    autoDownloadPhotos = autoDownloadPhotos,
    autoDownloadVideos = autoDownloadVideos,
    autoDownloadAudio = autoDownloadAudio,
    autoDownloadFiles = autoDownloadFiles,
    autoDownloadMaxMb = autoDownloadMaxMb,
    groupReplies = ContractGroupRepliesMode.valueOf(groupReplies.name),
    customStyles = customStyles.stream().map { it.toContract() }.toList(),
    closeToTray = closeToTray,
    launchAtLogin = launchAtLogin,
    resumeOnStartup = resumeOnStartup,
    rules = rules.entries.stream().collect(Collectors.toMap({ it.key }, { it.value.toContract() })),
)

fun ContractSettingsUpdate.toDomain(): SettingsUpdate = SettingsUpdate(
    paused = paused,
    maxParallelTasks = maxParallelTasks,
    defaultModel = defaultModel,
    defaultModelProfileId = defaultModelProfileId,
    deliveryMode = deliveryMode?.let { DomainDeliveryMode.valueOf(it.name) },
    ollamaDevice = ollamaDevice,
    globalPrompt = globalPrompt,
    replyDelaySeconds = replyDelaySeconds,
    replyIntervalSeconds = replyIntervalSeconds,
    showTyping = showTyping,
    formatReplies = formatReplies,
    replyProcessingMode = replyProcessingMode?.let { DomainReplyProcessingMode.valueOf(it.name) },
    ignoreAssistantTasks = ignoreAssistantTasks,
    memoryEnabled = memoryEnabled,
    memoryCollectionScope = memoryCollectionScope?.let { DomainMemoryCollectionScope.valueOf(it.name) },
    proactiveEnabled = proactiveEnabled,
    proactiveIntervalMinutes = proactiveIntervalMinutes,
    memoryIntervalHours = memoryIntervalHours,
    autoDownloadPhotos = autoDownloadPhotos,
    autoDownloadVideos = autoDownloadVideos,
    autoDownloadAudio = autoDownloadAudio,
    autoDownloadFiles = autoDownloadFiles,
    autoDownloadMaxMb = autoDownloadMaxMb,
    groupReplies = groupReplies?.let { DomainGroupReplyMode.valueOf(it.name) },
    customStyles = customStyles?.stream()?.map { it.toDomain() }?.toList(),
    closeToTray = closeToTray,
    launchAtLogin = launchAtLogin,
    resumeOnStartup = resumeOnStartup,
)

fun DomainWritingStyle.toContract(): WritingStyle = WritingStyle(id, name, emoji, instructions)

fun WritingStyle.toDomain(): DomainWritingStyle = DomainWritingStyle(id, name, emoji, instructions)
