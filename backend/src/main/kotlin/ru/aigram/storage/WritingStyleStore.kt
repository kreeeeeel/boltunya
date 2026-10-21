package ru.aigram.storage

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.LinkedHashMap
import java.util.stream.Collectors
import java.util.stream.Stream

private const val MAX_STYLE_SAMPLES = 50L

@Component
class WritingStyleStore(directory: Path) {
    private val file = directory.resolve("writing-style.json")
    private var samples = if (Files.exists(file)) {
        Json.decodeFromString<Map<String, List<ChatMessage>>>(Files.readString(file))
    } else {
        emptyMap()
    }

    @Synchronized
    fun update(chatId: String, history: List<ChatMessage>): List<ChatMessage> {
        val incoming = history.stream().filter {
            it.outgoing && !it.automated && it.kind == "text" &&
                it.text.length in 1..600
        }
            .map { ChatMessage(it.id, chatId, it.text, true, it.date) }.toList()
        val combined = Stream.concat(samples[chatId].orEmpty().stream(), incoming.stream()).collect(
            Collectors.toMap({ it.id }, { it }, { _, recent -> recent }, { LinkedHashMap() }),
        )
        val next = combined.values.stream().skip((combined.size - MAX_STYLE_SAMPLES).coerceAtLeast(0)).toList()
        if (samples[chatId] == next || next.isEmpty()) {
            return next
        }
        val updated = (samples + (chatId to next)).entries.toList().takeLast(200).associate { it.toPair() }
        persist(updated)
        return next
    }

    @Synchronized
    fun forget(chatId: String, messageIds: Set<String>) {
        val previous = samples[chatId] ?: return
        val retained = previous.stream().filter { it.id !in messageIds }.toList()
        if (retained == previous) {
            return
        }
        val updated = if (retained.isEmpty()) {
            samples - chatId
        } else {
            samples + (chatId to retained)
        }
        persist(updated)
    }

    @Synchronized
    fun clear() {
        Files.deleteIfExists(file)
        samples = emptyMap()
    }

    private fun persist(updated: Map<String, List<ChatMessage>>) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("writing-style.tmp")
        Files.writeString(temporary, Json.encodeToString(updated))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        samples = updated
    }
}
