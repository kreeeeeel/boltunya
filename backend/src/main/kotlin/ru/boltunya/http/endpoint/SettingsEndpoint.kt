package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.boltunya.ai.EngineManager
import ru.boltunya.ai.ModelInference
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.AppSettings
import ru.boltunya.domain.GroupReplyMode
import ru.boltunya.domain.SettingsUpdate
import ru.boltunya.exception.impl.DuplicateStyleIdentifierException
import ru.boltunya.exception.impl.InvalidSettingTypeException
import ru.boltunya.http.InputValidation
import ru.boltunya.http.SettingKey
import ru.boltunya.storage.SettingsStore

class SettingsEndpoint @Inject constructor(
    private val json: Json,
    private val settings: SettingsStore,
    private val engine: EngineManager,
    private val replies: ReplyCoordinator,
    private val validation: InputValidation,
    private val inference: ModelInference,
) {
    fun update(body: JsonObject): AppSettings {
        body.entries.stream().forEach { (name, value) ->
            val key = SettingKey.entries.firstOrNull { it.jsonName == name }
            if (key == null || !key.accepts(value)) throw InvalidSettingTypeException()
        }
        val update = json.decodeFromJsonElement<SettingsUpdate>(body)
        validation.validate(update)
        if (update.groupReplies == GroupReplyMode.INHERIT) throw InvalidSettingTypeException()
        if (update.customStyles != null &&
            update.customStyles.stream().map { it.id }.distinct().count() != update.customStyles.size.toLong()
        ) {
            throw DuplicateStyleIdentifierException()
        }
        if (update.defaultModel != null) {
            validation.model(update.defaultModel)
            engine.warm(update.defaultModel)
        }
        replies.cancelPending()
        val saved = settings.update {
            it.copy(
                paused = update.paused ?: it.paused, globalPrompt = update.globalPrompt ?: it.globalPrompt,
                defaultModel = update.defaultModel ?: it.defaultModel,
                replyDelaySeconds = update.replyDelaySeconds ?: it.replyDelaySeconds,
                replyIntervalSeconds =
                update.replyIntervalSeconds ?: it.replyIntervalSeconds,
                showTyping = update.showTyping ?: it.showTyping,
                ignoreAssistantTasks =
                update.ignoreAssistantTasks ?: it.ignoreAssistantTasks,
                groupReplies = update.groupReplies ?: it.groupReplies,
                memoryEnabled = update.memoryEnabled ?: it.memoryEnabled,
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
        if (!saved.memoryEnabled) inference.interruptBackground()
        return saved
    }
}
