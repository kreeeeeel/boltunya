package ru.boltunya.storage

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import ru.boltunya.domain.ChatMessage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class WritingStyleStore @Inject constructor(directory: Path) {
    private val file = directory.resolve("writing-style.json")
    private var samples = if (Files.exists(file)) {
        Json.decodeFromString<Map<String, List<ChatMessage>>>(Files.readString(file))
    } else {
        emptyMap()
    }

    @Synchronized
    fun update(chatId: String, history: List<ChatMessage>): List<ChatMessage> {
        val incoming = history.stream().filter { it.outgoing && it.kind == "text" && it.text.length in 1..600 }
            .map { ChatMessage(it.id, chatId, it.text, true, it.date) }.toList()
        val next = (samples[chatId].orEmpty() + incoming).distinctBy { it.id }.takeLast(50)
        if (samples[chatId] == next || next.isEmpty()) return next
        val updated = (samples + (chatId to next)).entries.toList().takeLast(200).associate { it.toPair() }
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("writing-style.tmp")
        Files.writeString(temporary, Json.encodeToString(updated))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        samples = updated
        return next
    }

    @Synchronized
    fun clear() {
        Files.deleteIfExists(file)
        samples = emptyMap()
    }
}
