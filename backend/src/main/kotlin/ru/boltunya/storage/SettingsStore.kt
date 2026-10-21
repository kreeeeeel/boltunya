package ru.boltunya.storage

import kotlinx.serialization.json.Json
import ru.boltunya.domain.AppSettings
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.CommunicationStyle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class SettingsStore(private val directory: Path) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val file = directory.resolve("settings.json")

    @Volatile
    private var settings = if (Files.exists(file)) {
        val saved = json.decodeFromString<AppSettings>(Files.readString(file))
        saved.copy(paused = !saved.resumeOnStartup)
    } else {
        AppSettings()
    }

    fun get(): AppSettings = settings

    fun rule(chatId: String): ChatRule = resolveModel(settings.rules[chatId] ?: ChatRule())

    fun resolveModel(rule: ChatRule): ChatRule = if (rule.useDefaultModel) {
        rule.copy(
            model = settings.defaultModel,
            modelProfileId = settings.defaultModelProfileId,
        )
    } else {
        rule
    }

    fun resolve(rule: ChatRule): ChatRule {
        val resolved = resolveModel(rule).copy(resolvedStyleInstructions = "")
        if (resolved.style != CommunicationStyle.CUSTOM.key) return resolved
        val style = settings.customStyles.stream().filter { it.id == resolved.styleId }.findFirst().orElse(null)
            ?: return resolved.copy(style = CommunicationStyle.FRIENDLY.key, styleId = "")
        return resolved.copy(resolvedStyleInstructions = style.instructions)
    }

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val next = transform(settings)
        Files.createDirectories(directory)
        val temporary = directory.resolve("settings.tmp")
        Files.writeString(temporary, json.encodeToString(next))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        settings = next
        return next
    }

    fun saveRule(chatId: String, rule: ChatRule) {
        update { it.copy(rules = it.rules + (chatId to rule)) }
    }
}
