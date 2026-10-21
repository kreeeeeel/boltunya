package ru.aigram.memory

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import ru.aigram.domain.ChatMessage
import ru.aigram.storage.AutomatedReplyStore

private const val MAX_SOURCE_CHARACTERS = 1000
private const val MAX_BATCH_CHARACTERS = 7000

class MemoryBatchBuilder @Inject constructor(private val automated: AutomatedReplyStore) {
    fun select(messages: List<ChatMessage>): MemoryBatch {
        var characters = 0
        val sources = mutableListOf<MemorySource>()
        val selected = messages.asReversed().stream().takeWhile { message ->
            if (message.contextExcluded) {
                true
            } else {
                val source = MemorySource(
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
                    message.senderUsername,
                )
                val size = Json.encodeToString(source).length
                if (characters + size <= MAX_BATCH_CHARACTERS) {
                    characters += size
                    sources.add(source)
                    true
                } else {
                    false
                }
            }
        }.toList().asReversed()
        return MemoryBatch(selected, sources.asReversed())
    }
}
