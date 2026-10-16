package ru.boltunya.http

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

enum class SettingKey(val jsonName: String, private val kind: SettingValueKind) {
    PAUSED("paused", SettingValueKind.BOOLEAN),
    SHOW_TYPING("showTyping", SettingValueKind.BOOLEAN),
    IGNORE_ASSISTANT_TASKS("ignoreAssistantTasks", SettingValueKind.BOOLEAN),
    MEMORY_ENABLED("memoryEnabled", SettingValueKind.BOOLEAN),
    MEMORY_INTERVAL_HOURS("memoryIntervalHours", SettingValueKind.INTEGER),
    AUTO_DOWNLOAD_PHOTOS("autoDownloadPhotos", SettingValueKind.BOOLEAN),
    AUTO_DOWNLOAD_VIDEOS("autoDownloadVideos", SettingValueKind.BOOLEAN),
    AUTO_DOWNLOAD_AUDIO("autoDownloadAudio", SettingValueKind.BOOLEAN),
    AUTO_DOWNLOAD_FILES("autoDownloadFiles", SettingValueKind.BOOLEAN),
    AUTO_DOWNLOAD_MAX_MB("autoDownloadMaxMb", SettingValueKind.INTEGER),
    CLOSE_TO_TRAY("closeToTray", SettingValueKind.BOOLEAN),
    LAUNCH_AT_LOGIN("launchAtLogin", SettingValueKind.BOOLEAN),
    RESUME_ON_STARTUP("resumeOnStartup", SettingValueKind.BOOLEAN),
    REPLY_DELAY_SECONDS("replyDelaySeconds", SettingValueKind.INTEGER),
    REPLY_INTERVAL_SECONDS("replyIntervalSeconds", SettingValueKind.INTEGER),
    DEFAULT_MODEL("defaultModel", SettingValueKind.STRING),
    GLOBAL_PROMPT("globalPrompt", SettingValueKind.STRING),
    GROUP_REPLIES("groupReplies", SettingValueKind.STRING),
    CUSTOM_STYLES("customStyles", SettingValueKind.LIST),
    ;

    fun accepts(value: JsonElement): Boolean {
        if (kind == SettingValueKind.LIST) return value is JsonArray
        val primitive = value as? JsonPrimitive ?: return false
        return when (kind) {
            SettingValueKind.BOOLEAN -> !primitive.isString && primitive.booleanOrNull != null
            SettingValueKind.INTEGER -> !primitive.isString && primitive.intOrNull != null
            SettingValueKind.STRING -> primitive.isString
            SettingValueKind.LIST -> false
        }
    }
}
