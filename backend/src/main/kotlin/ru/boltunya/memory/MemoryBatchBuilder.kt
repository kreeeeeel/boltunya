package ru.boltunya.memory

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import ru.boltunya.domain.ChatMessage
import ru.boltunya.storage.AutomatedReplyStore

private const val MAX_SOURCE_CHARACTERS = 1000
private const val MAX_BATCH_CHARACTERS = 7000

class MemoryBatchBuilder @Inject constructor(private val automated: AutomatedReplyStore) {
    fun select(messages: List<ChatMessage>): MemoryBatch {
        var characters = 0
        val sources = messages.asReversed().stream().map { message ->
            MemorySource(
                message.id,
                message.date,
                if (!message.outgoing) {
                    MemorySpeaker.CONTACT
                } else if (message.automated || automated.contains(message.chatId, message.id)) {
                    MemorySpeaker.ASSISTANT
                } else {
                    MemorySpeaker.OWNER
                },
                message.text.take(MAX_SOURCE_CHARACTERS),
                message.kind,
                message.senderName,
                message.senderId,
            )
        }.takeWhile { source ->
            characters += Json.encodeToString(source).length
            characters <= MAX_BATCH_CHARACTERS
        }.toList().asReversed()
        val ids = sources.map { it.id }.toSet()
        return MemoryBatch(messages.filter { it.id in ids }, sources)
    }
}
