package ru.aigram.memory

import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.memory.MemoryBatch
import ru.aigram.domain.memory.MemorySource
import ru.aigram.domain.memory.MemorySpeaker
import ru.aigram.exception.impl.MemorySourceContextLimitException
import ru.aigram.storage.AutomatedReplyStore
import java.util.stream.IntStream

private const val BUDGET_SEARCH_STEPS = 32

@Component
class MemoryBatchBuilder(private val automated: AutomatedReplyStore) {
    fun select(messages: List<ChatMessage>, fits: (List<MemorySource>) -> Boolean = { true }): MemoryBatch {
        val positions = IntStream.range(0, messages.size).filter {
            !messages[it].contextExcluded && !messages[it].deleted
        }.boxed().toList()
        val sources = positions.stream().map { source(messages[it]) }.toList()
        if (sources.isEmpty() || fits(sources)) {
            return MemoryBatch(messages, sources)
        }
        if (!fits(sources.takeLast(1))) {
            throw MemorySourceContextLimitException()
        }

        var low = 1
        var high = sources.size - 1
        IntStream.range(0, BUDGET_SEARCH_STEPS).forEach {
            if (low < high) {
                val middle = (low + high + 1) / 2
                if (fits(sources.subList(sources.size - middle, sources.size))) {
                    low = middle
                } else {
                    high = middle - 1
                }
            }
        }
        // Excluded/deleted messages before the first retained source still advance the pagination cursor.
        val first = positions[positions.size - low - 1] + 1
        return MemoryBatch(messages.subList(first, messages.size), sources.subList(sources.size - low, sources.size))
    }

    private fun source(message: ChatMessage): MemorySource = MemorySource(
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
}
