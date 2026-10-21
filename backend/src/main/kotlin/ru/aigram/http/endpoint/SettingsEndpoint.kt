package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.springframework.stereotype.Component
import ru.aigram.ai.EngineManager
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaDeviceSelector
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.AppSettings
import ru.aigram.domain.ChatRule
import ru.aigram.domain.DeliveryMode
import ru.aigram.domain.GroupReplyMode
import ru.aigram.domain.SettingsUpdate
import ru.aigram.exception.impl.DuplicateStyleIdentifierException
import ru.aigram.exception.impl.InvalidSettingTypeException
import ru.aigram.http.InputValidation
import ru.aigram.http.SettingKey
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.storage.SettingsStore

private val REPLY_SETTINGS = setOf(
    SettingKey.PAUSED,
    SettingKey.DEFAULT_MODEL,
    SettingKey.DEFAULT_MODEL_PROFILE_ID,
    SettingKey.GLOBAL_PROMPT,
    SettingKey.CUSTOM_STYLES,
    SettingKey.GROUP_REPLIES,
    SettingKey.DELIVERY_MODE,
    SettingKey.IGNORE_ASSISTANT_TASKS,
)

@Component
class SettingsEndpoint @Inject constructor(
    private val json: Json,
    private val settings: SettingsStore,
    private val engine: EngineManager,
    private val replies: ReplyCoordinator,
    private val validation: InputValidation,
    private val inference: ModelInference,
    private val devices: OllamaDeviceSelector,
    private val targets: ModelTargetResolver,
    private val memory: MemoryCoordinator,
) {
    fun update(body: JsonObject): AppSettings {
        body.entries.stream().forEach { (name, value) ->
            val key = SettingKey.entries.firstOrNull { it.jsonName == name }
            if (key == null || !key.accepts(value)) throw InvalidSettingTypeException()
        }
        val update = json.decodeFromJsonElement<SettingsUpdate>(body)
        validation.validate(update)
        if (update.ollamaDevice != null) devices.validate(update.ollamaDevice)
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
        if (update.defaultModel != null || update.defaultModelProfileId != null) {
            validation.selection(model, profileId, forAutomation = !(update.paused ?: current.paused))
        }
        if (update.paused == false) validation.target(ChatRule(), forAutomation = true)
        val saved = settings.update {
            it.copy(
                maxParallelTasks = update.maxParallelTasks ?: it.maxParallelTasks,
                paused = update.paused ?: it.paused, globalPrompt = update.globalPrompt ?: it.globalPrompt,
                defaultModel = update.defaultModel ?: it.defaultModel,
                defaultModelProfileId = update.defaultModelProfileId ?: it.defaultModelProfileId,
                deliveryMode = update.deliveryMode ?: it.deliveryMode,
                ollamaDevice = update.ollamaDevice ?: it.ollamaDevice,
                replyDelaySeconds = update.replyDelaySeconds ?: it.replyDelaySeconds,
                replyIntervalSeconds =
                update.replyIntervalSeconds ?: it.replyIntervalSeconds,
                showTyping = update.showTyping ?: it.showTyping,
                ignoreAssistantTasks =
                update.ignoreAssistantTasks ?: it.ignoreAssistantTasks,
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
                customStyles =
                update.customStyles ?: it.customStyles,
                closeToTray = update.closeToTray ?: it.closeToTray,
                launchAtLogin =
                update.launchAtLogin ?: it.launchAtLogin,
                resumeOnStartup =
                update.resumeOnStartup ?: it.resumeOnStartup,
            )
        }
        val before = json.encodeToJsonElement(current).jsonObject
        val after = json.encodeToJsonElement(saved).jsonObject
        if (REPLY_SETTINGS.any { before[it.jsonName] != after[it.jsonName] }) replies.cancelReplies()
        if (!saved.memoryEnabled) {
            memory.pause()
            inference.interruptBackground()
        }
        if (update.defaultModel != null || update.defaultModelProfileId != null || update.paused == false) {
            targets.startupWarmModel()?.let(engine::warm)
        }
        return saved
    }
}
