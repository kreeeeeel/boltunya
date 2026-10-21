package ru.aigram.storage

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.AppearanceSettings
import ru.aigram.exception.impl.InvalidAppearanceException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val MAX_CSS_LENGTH = 20_000
private val appearanceModes = setOf("light", "dark", "system")
private val appearanceTokens = setOf("accent", "background", "panel", "text", "muted", "incoming", "outgoing", "border")
private val colorLengths = setOf(4, 5, 7, 9)

@Component
class AppearanceStore @Inject constructor(private val directory: Path, private val json: Json) {
    private val file = directory.resolve("appearance.json")

    @Synchronized
    fun get(): AppearanceSettings = if (Files.exists(file)) {
        json.decodeFromString<AppearanceSettings>(Files.readString(file)).also(::validate)
    } else {
        AppearanceSettings()
    }

    @Synchronized
    fun save(value: AppearanceSettings): AppearanceSettings {
        validate(value)
        Files.createDirectories(directory)
        val temporary = directory.resolve("appearance.tmp")
        Files.writeString(temporary, json.encodeToString(value))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return value
    }

    private fun validate(value: AppearanceSettings) {
        if (value.mode !in appearanceModes || value.css.length > MAX_CSS_LENGTH ||
            value.tokens.entries.stream().anyMatch { (name, color) ->
                name !in appearanceTokens || color.length !in colorLengths || !color.startsWith('#') ||
                    color.substring(1).chars().anyMatch { Character.digit(it, 16) < 0 }
            }
        ) {
            throw InvalidAppearanceException()
        }
    }
}
