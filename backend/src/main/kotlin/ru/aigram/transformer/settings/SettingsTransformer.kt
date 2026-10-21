package ru.aigram.transformer.settings

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.Settings
import ru.aigram.contract.dto.WritingStyle
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.settings.SettingsUpdate
import ru.aigram.transformer.chat.ChatRuleTransformer
import java.util.stream.Collectors
import ru.aigram.contract.dto.DefaultDeliveryMode as ContractDefaultDeliveryMode
import ru.aigram.contract.dto.GroupRepliesMode as ContractGroupRepliesMode
import ru.aigram.contract.dto.MemoryCollectionScope as ContractMemoryCollectionScope
import ru.aigram.contract.dto.SettingsUpdate as ContractSettingsUpdate
import ru.aigram.domain.chat.DeliveryMode as DomainDeliveryMode
import ru.aigram.domain.chat.GroupReplyMode as DomainGroupReplyMode
import ru.aigram.domain.chat.WritingStyle as DomainWritingStyle
import ru.aigram.domain.memory.MemoryCollectionScope as DomainMemoryCollectionScope

@Component
class SettingsTransformer(private val rules: ChatRuleTransformer) {
    fun toContract(value: AppSettings): Settings = Settings(
        paused = value.paused,
        maxParallelTasks = value.maxParallelTasks,
        defaultModel = value.defaultModel,
        defaultModelProfileId = value.defaultModelProfileId,
        deliveryMode = ContractDefaultDeliveryMode.valueOf(value.deliveryMode.name),
        ollamaDevice = value.ollamaDevice,
        globalPrompt = value.globalPrompt,
        replyDelaySeconds = value.replyDelaySeconds,
        replyIntervalSeconds = value.replyIntervalSeconds,
        showTyping = value.showTyping,
        ignoreAssistantTasks = value.ignoreAssistantTasks,
        memoryEnabled = value.memoryEnabled,
        memoryCollectionScope = ContractMemoryCollectionScope.valueOf(value.memoryCollectionScope.name),
        proactiveEnabled = value.proactiveEnabled,
        proactiveIntervalMinutes = value.proactiveIntervalMinutes,
        memoryIntervalHours = value.memoryIntervalHours,
        autoDownloadPhotos = value.autoDownloadPhotos,
        autoDownloadVideos = value.autoDownloadVideos,
        autoDownloadAudio = value.autoDownloadAudio,
        autoDownloadFiles = value.autoDownloadFiles,
        autoDownloadMaxMb = value.autoDownloadMaxMb,
        groupReplies = ContractGroupRepliesMode.valueOf(value.groupReplies.name),
        customStyles = value.customStyles.stream().map {
            WritingStyle(it.id, it.name, it.emoji, it.instructions)
        }.toList(),
        closeToTray = value.closeToTray,
        launchAtLogin = value.launchAtLogin,
        resumeOnStartup = value.resumeOnStartup,
        rules = value.rules.entries.stream().collect(Collectors.toMap({ it.key }, { rules.toContract(it.value) })),
    )

    fun toDomain(value: ContractSettingsUpdate): SettingsUpdate = SettingsUpdate(
        paused = value.paused,
        maxParallelTasks = value.maxParallelTasks,
        defaultModel = value.defaultModel,
        defaultModelProfileId = value.defaultModelProfileId,
        deliveryMode = value.deliveryMode?.let { DomainDeliveryMode.valueOf(it.name) },
        ollamaDevice = value.ollamaDevice,
        globalPrompt = value.globalPrompt,
        replyDelaySeconds = value.replyDelaySeconds,
        replyIntervalSeconds = value.replyIntervalSeconds,
        showTyping = value.showTyping,
        ignoreAssistantTasks = value.ignoreAssistantTasks,
        memoryEnabled = value.memoryEnabled,
        memoryCollectionScope = value.memoryCollectionScope?.let { DomainMemoryCollectionScope.valueOf(it.name) },
        proactiveEnabled = value.proactiveEnabled,
        proactiveIntervalMinutes = value.proactiveIntervalMinutes,
        memoryIntervalHours = value.memoryIntervalHours,
        autoDownloadPhotos = value.autoDownloadPhotos,
        autoDownloadVideos = value.autoDownloadVideos,
        autoDownloadAudio = value.autoDownloadAudio,
        autoDownloadFiles = value.autoDownloadFiles,
        autoDownloadMaxMb = value.autoDownloadMaxMb,
        groupReplies = value.groupReplies?.let { DomainGroupReplyMode.valueOf(it.name) },
        customStyles = value.customStyles?.stream()?.map {
            DomainWritingStyle(it.id, it.name, it.emoji, it.instructions)
        }?.toList(),
        closeToTray = value.closeToTray,
        launchAtLogin = value.launchAtLogin,
        resumeOnStartup = value.resumeOnStartup,
    )
}
