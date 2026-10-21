package ru.aigram.storage

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Component
class AutomatedReplyStore @Inject constructor(directory: Path) {
    private val file = directory.resolve("automated-replies.json")

    @Volatile
    private var ids = if (Files.exists(file)) {
        Json.decodeFromString<List<String>>(Files.readString(file)).toSet()
    } else {
        emptySet()
    }

    fun contains(chatId: String, messageId: String): Boolean = "$chatId:$messageId" in ids

    @Synchronized
    fun record(chatId: String, messageId: String) {
        val next = (ids + "$chatId:$messageId").toList()
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("automated-replies.tmp")
        Files.writeString(temporary, Json.encodeToString(next))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        ids = next.toSet()
    }

    @Synchronized
    fun clear() {
        Files.deleteIfExists(file)
        ids = emptySet()
    }
}
