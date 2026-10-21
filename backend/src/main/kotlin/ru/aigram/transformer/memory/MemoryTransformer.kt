package ru.aigram.transformer.memory

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.MemoryCollectionProgress
import ru.aigram.contract.dto.MemoryCollectionStatus
import ru.aigram.contract.dto.MemoryDocument
import ru.aigram.domain.memory.MemoryDocument as DomainMemoryDocument

@Component
class MemoryTransformer {
    fun toContract(value: DomainMemoryDocument): MemoryDocument = MemoryDocument(
        chatId = value.chatId,
        notes = value.notes,
        summary = value.summary,
        revision = value.revision,
        updatedAt = value.updatedAt,
        processedMessages = value.processedMessages,
        backfillComplete = value.backfillComplete,
        status = MemoryCollectionStatus.valueOf(value.status.name),
        error = value.error,
        trackedChats = value.trackedChats,
        relativePath = value.relativePath,
        collection = MemoryCollectionProgress(
            value.collection.active,
            MemoryCollectionStatus.valueOf(value.collection.status.name),
            value.collection.queuedChats,
            value.collection.queuePosition,
            value.collection.processedMessages,
        ),
    )
}
