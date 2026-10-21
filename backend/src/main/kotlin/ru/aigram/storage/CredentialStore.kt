package ru.aigram.storage

import com.sun.jna.platform.win32.Crypt32Util
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.TelegramCredentials
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Component
class CredentialStore @Inject constructor(private val directory: Path) {
    private val file = directory.resolve("telegram.credentials")

    fun exists(): Boolean = Files.exists(file)

    @Synchronized
    fun save(credentials: TelegramCredentials) {
        Files.createDirectories(directory)
        val bytes = Json.encodeToString(credentials).toByteArray(Charsets.UTF_8)
        val temporary = directory.resolve("telegram.credentials.tmp")
        Files.write(temporary, Crypt32Util.cryptProtectData(bytes))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun load(): TelegramCredentials? {
        if (!Files.exists(file)) {
            return null
        }
        val bytes = Crypt32Util.cryptUnprotectData(Files.readAllBytes(file))
        return Json.decodeFromString<TelegramCredentials>(String(bytes, Charsets.UTF_8))
    }

    fun delete() {
        Files.deleteIfExists(file)
    }
}
