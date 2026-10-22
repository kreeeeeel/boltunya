package ru.aigram.component.settings

import org.springframework.stereotype.Component
import ru.aigram.ai.EngineManager
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaDeviceSelector
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.chat.DeliveryMode
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.settings.SettingsUpdate
import ru.aigram.exception.impl.DuplicateStyleIdentifierException
import ru.aigram.exception.impl.InvalidSettingTypeException
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.speech.reply.VoiceSettingsValidator
import ru.aigram.storage.SettingsStore
import ru.aigram.validation.InputValidation

@Component
class SettingsComponent(
    private val settings: SettingsStore,
    private val engine: EngineManager,
    private val replies: ReplyCoordinator,
    private val validation: InputValidation,
    private val inference: ModelInference,
    private val devices: OllamaDeviceSelector,
    private val targets: ModelTargetResolver,
    private val memory: MemoryCoordinator,
    private val voiceSettings: VoiceSettingsValidator,
) {
    fun update(update: SettingsUpdate): AppSettings {
        validation.validate(update)
        voiceSettings.validate(update)
        if (update.ollamaDevice != null) {
            devices.validate(update.ollamaDevice)
        }
        if (update.groupReplies == GroupReplyMode.INHERIT || update.deliveryMode == DeliveryMode.INHERIT) {
            throw InvalidSettingTypeException()
        }
        if (update.customStyles != null &&
            update.customStyles.stream().map { it.id }.distinct().count() != update.customStyles.size.toLong()
        ) {
            throw DuplicateStyleIdentifierException()
        }

        val current = settings.get()
        val model = update.defaultModel ?: current.defaultModel
        val profileId = update.defaultModelProfileId ?: current.defaultModelProfileId
        if (update.defaultModel != null || update.defaultModelProfileId != null || update.paused == false) {
            validation.selection(model, profileId, forAutomation = !(update.paused ?: current.paused))
        }

        val saved = settings.update {
            it.copy(
                maxParallelTasks = update.maxParallelTasks ?: it.maxParallelTasks,
                paused = update.paused ?: it.paused,
                globalPrompt = update.globalPrompt ?: it.globalPrompt,
                defaultModel = update.defaultModel ?: it.defaultModel,
                defaultModelProfileId = update.defaultModelProfileId ?: it.defaultModelProfileId,
                deliveryMode = update.deliveryMode ?: it.deliveryMode,
                ollamaDevice = update.ollamaDevice ?: it.ollamaDevice,
                replyDelaySeconds = update.replyDelaySeconds ?: it.replyDelaySeconds,
                replyIntervalSeconds = update.replyIntervalSeconds ?: it.replyIntervalSeconds,
                showTyping = update.showTyping ?: it.showTyping,
                formatReplies = update.formatReplies ?: it.formatReplies,
                voiceInputEnabled = update.voiceInputEnabled ?: it.voiceInputEnabled,
                voiceReplyMode = update.voiceReplyMode ?: it.voiceReplyMode,
                voiceReplyProbability = update.voiceReplyProbability ?: it.voiceReplyProbability,
                voiceRecognitionModel = update.voiceRecognitionModel ?: it.voiceRecognitionModel,
                voiceSynthesisModel = update.voiceSynthesisModel ?: it.voiceSynthesisModel,
                voiceSpeechRate = update.voiceSpeechRate ?: it.voiceSpeechRate,
                replyProcessingMode = update.replyProcessingMode ?: it.replyProcessingMode,
                ignoreAssistantTasks = update.ignoreAssistantTasks ?: it.ignoreAssistantTasks,
                groupReplies = update.groupReplies ?: it.groupReplies,
                memoryEnabled = update.memoryEnabled ?: it.memoryEnabled,
                memoryCollectionScope = update.memoryCollectionScope ?: it.memoryCollectionScope,
                proactiveEnabled = update.proactiveEnabled ?: it.proactiveEnabled,
                proactiveIntervalMinutes = update.proactiveIntervalMinutes ?: it.proactiveIntervalMinutes,
                memoryIntervalHours = update.memoryIntervalHours ?: it.memoryIntervalHours,
                autoDownloadPhotos = update.autoDownloadPhotos ?: it.autoDownloadPhotos,
                autoDownloadVideos = update.autoDownloadVideos ?: it.autoDownloadVideos,
                autoDownloadAudio = update.autoDownloadAudio ?: it.autoDownloadAudio,
                autoDownloadFiles = update.autoDownloadFiles ?: it.autoDownloadFiles,
                autoDownloadMaxMb = update.autoDownloadMaxMb ?: it.autoDownloadMaxMb,
                customStyles = update.customStyles ?: it.customStyles,
                closeToTray = update.closeToTray ?: it.closeToTray,
                launchAtLogin = update.launchAtLogin ?: it.launchAtLogin,
                resumeOnStartup = update.resumeOnStartup ?: it.resumeOnStartup,
            )
        }

        if (replySettingsChanged(current, saved)) {
            replies.cancelReplies()
        } else {
            replies.cancelVoiceReplies(current, saved)
        }

        if (!saved.memoryEnabled) {
            memory.pause()
            inference.interruptBackground()
        }

        if (saved.paused && !current.paused) {
            inference.interruptBackground()
            engine.pauseInference()
        }

        if (update.defaultModel != null || update.defaultModelProfileId != null || update.paused == false) {
            if (!saved.paused) {
                engine.start()
            }
            targets.startupWarmModel()?.let(engine::warm)
        }

        return saved
    }

    private fun replySettingsChanged(before: AppSettings, after: AppSettings): Boolean =
        before.paused != after.paused || before.defaultModel != after.defaultModel ||
            before.defaultModelProfileId != after.defaultModelProfileId || before.globalPrompt != after.globalPrompt ||
            before.customStyles != after.customStyles || before.groupReplies != after.groupReplies ||
            before.deliveryMode != after.deliveryMode || before.ignoreAssistantTasks != after.ignoreAssistantTasks ||
            before.formatReplies != after.formatReplies ||
            before.replyProcessingMode != after.replyProcessingMode
}
