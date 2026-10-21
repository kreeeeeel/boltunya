package ru.aigram.memory

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.ChatMessage
import ru.aigram.exception.impl.MemorySourceContextLimitException
import ru.aigram.storage.AutomatedReplyStore

private const val MAX_BATCH_CHARACTERS = 7000

@Component
class MemoryBatchBuilder @Inject constructor(private val automated: AutomatedReplyStore) {
    fun select(
        messages: List<ChatMessage>,
        fits: (List<MemorySource>) -> Boolean = { Json.encodeToString(it).length <= MAX_BATCH_CHARACTERS },
    ): MemoryBatch {
        val sources = mutableListOf<MemorySource>()
        val selected = messages.asReversed().stream().takeWhile { message ->
            if (message.contextExcluded || message.deleted) {
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
                    message.text,
                    message.kind,
                    message.senderName,
                    message.senderId,
                    message.senderUsername,
                )
                if (sources.isEmpty() && !fits(listOf(source))) {
                    throw MemorySourceContextLimitException()
                }
                if (fits((sources + source).asReversed())) {
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
