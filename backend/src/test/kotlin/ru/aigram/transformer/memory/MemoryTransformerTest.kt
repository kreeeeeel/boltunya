package ru.aigram.transformer.memory

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.MemoryCollectionStatus
import ru.aigram.domain.memory.MemoryCollectionProgress
import ru.aigram.domain.memory.MemoryDocument
import ru.aigram.domain.memory.MemoryStatus
import kotlin.test.assertEquals

class MemoryTransformerTest {
    @Test
    fun `memory response preserves editable revision notes and collection progress independently`() {
        val value = MemoryDocument(
            "-1009007199254740993", "Manual **note**", "Collected facts", "revision-1", updatedAt = 4_000_000_000L,
            processedMessages = 700, status = MemoryStatus.WAITING_MODEL, trackedChats = 2,
            collection = MemoryCollectionProgress(
                active = true,
                status = MemoryStatus.QUEUED,
                queuedChats = 3,
                queuePosition = 2,
                processedMessages = 400,
            ),
        )

        val mapped = MemoryTransformer().toContract(value)

        assertEquals(value.chatId, mapped.chatId)
        assertEquals("Manual **note**", mapped.notes)
        assertEquals("revision-1", mapped.revision)
        assertEquals(4_000_000_000L, mapped.updatedAt)
        assertEquals(MemoryCollectionStatus.WAITING_MODEL, mapped.status)
        assertEquals(MemoryCollectionStatus.QUEUED, mapped.collection?.status)
        assertEquals(2, mapped.collection?.queuePosition)
        assertEquals(400L, mapped.collection?.processedMessages)
    }
}
