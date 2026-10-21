package ru.aigram.automation.proactive

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import ru.aigram.domain.ChatMessage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class ProactiveLedger @Inject constructor(directory: Path) {
    private val file = directory.resolve("automation/initiatives.json")
    private val json = Json { ignoreUnknownKeys = true }
    private var records: Map<String, ProactiveRecord> = if (Files.isRegularFile(file)) {
        json.decodeFromString(Files.readString(file))
    } else {
        emptyMap()
    }

    @Synchronized
    fun get(owner: String, chatId: String): ProactiveRecord? = records["$owner/$chatId"]

    @Synchronized
    fun reserve(owner: String, chatId: String, sourceId: String, now: Long): ProactiveRecord {
        val key = "$owner/$chatId"
        val next = ProactiveRecord(sourceId, now, records[key]?.lastIncomingId.orEmpty())
        save(key, next)
        return next
    }

    @Synchronized
    fun observe(owner: String, message: ChatMessage) {
        if (message.outgoing || message.deleted || message.automated) return
        val key = "$owner/${message.chatId}"
        val previous = records[key] ?: return
        val incoming = message.id.toLongOrNull() ?: return
        val source = previous.sourceMessageId.toLongOrNull() ?: return
        if (incoming <= maxOf(source, previous.lastIncomingId.toLongOrNull() ?: 0)) return
        save(key, previous.copy(lastIncomingId = message.id, awaitingResponse = false))
    }

    @Synchronized
    fun prepareDelivery(owner: String, chatId: String, reservation: ProactiveRecord): Boolean {
        val key = "$owner/$chatId"
        val current = records[key] ?: return false
        if (current.awaitingResponse || current.attemptedAt != reservation.attemptedAt ||
            current.lastIncomingId != reservation.lastIncomingId
        ) {
            return false
        }
        save(key, current.copy(awaitingResponse = true))
        return true
    }

    private fun save(key: String, value: ProactiveRecord) {
        val updated = records + (key to value)
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temporary, json.encodeToString(updated))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        records = updated
    }
}
