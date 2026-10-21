package ru.aigram.ai.profile

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.ai.profile.ProtectedModelCredential
import ru.aigram.exception.impl.InvalidModelProfileException
import ru.aigram.exception.impl.ModelCredentialMissingException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Windows DPAPI binds credentials to the current OS user; metadata contains no keys. */
@Component
class ModelCredentialVault(directory: Path) {
    private val directory = directory.resolve("models/credentials")

    fun exists(id: String): Boolean = Files.isRegularFile(file(id))

    @Synchronized
    fun load(id: String, expectedOrigin: String): String {
        val path = file(id)
        if (!Files.isRegularFile(path)) {
            return ""
        }
        val text = String(Crypt32Util.cryptUnprotectData(Files.readAllBytes(path)), Charsets.UTF_8)
        val credential = try {
            Json.decodeFromString<ProtectedModelCredential>(text)
        } catch (exception: SerializationException) {
            throw ModelCredentialMissingException(exception)
        }
        if (credential.origin != expectedOrigin) {
            throw ModelCredentialMissingException()
        }
        return credential.apiKey
    }

    @Synchronized
    fun save(id: String, value: String, origin: String) {
        if (value.isEmpty() || value.length > 8192 ||
            value.codePoints().anyMatch { Character.isWhitespace(it) || Character.isISOControl(it) }
        ) {
            throw InvalidModelProfileException()
        }
        val path = file(id)
        Files.createDirectories(directory)
        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        val payload = Json.encodeToString(ProtectedModelCredential(origin, value)).toByteArray(Charsets.UTF_8)
        Files.write(temporary, Crypt32Util.cryptProtectData(payload))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Synchronized
    fun delete(id: String) {
        Files.deleteIfExists(file(id))
    }

    private fun file(id: String): Path {
        val uuid = try {
            UUID.fromString(id)
        } catch (exception: IllegalArgumentException) {
            throw InvalidModelProfileException(exception)
        }
        if (uuid.toString() != id) {
            throw InvalidModelProfileException()
        }
        return directory.resolve("$id.credentials")
    }
}
