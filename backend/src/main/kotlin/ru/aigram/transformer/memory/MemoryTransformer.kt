package ru.aigram.transformer.memory

import ru.aigram.contract.dto.MemoryCollectionProgress
import ru.aigram.contract.dto.MemoryCollectionStatus
import ru.aigram.contract.dto.MemoryDocument
import ru.aigram.domain.memory.MemoryDocument as DomainMemoryDocument

fun DomainMemoryDocument.toContract(): MemoryDocument = MemoryDocument(
    chatId = chatId,
    notes = notes,
    summary = summary,
    revision = revision,
    updatedAt = updatedAt,
    processedMessages = processedMessages,
    backfillComplete = backfillComplete,
    status = MemoryCollectionStatus.valueOf(status.name),
    error = error,
    trackedChats = trackedChats,
    relativePath = relativePath,
    collection = MemoryCollectionProgress(
        collection.active,
        MemoryCollectionStatus.valueOf(collection.status.name),
        collection.queuedChats,
        collection.queuePosition,
        collection.processedMessages,
    ),
)
