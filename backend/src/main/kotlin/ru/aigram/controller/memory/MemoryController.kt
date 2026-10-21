package ru.aigram.controller.memory

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.memory.MemoryComponent
import ru.aigram.contract.api.MemoryApi
import ru.aigram.contract.dto.MemoryDocument
import ru.aigram.contract.dto.MemoryNotesUpdate
import ru.aigram.contract.dto.MemoryRefreshRequest
import ru.aigram.contract.dto.Ok
import ru.aigram.transformer.memory.MemoryTransformer

@RestController
class MemoryController(private val memory: MemoryComponent, private val transformer: MemoryTransformer) : MemoryApi {
    override fun getChatMemory(chatId: String): ResponseEntity<MemoryDocument> =
        ResponseEntity.ok(transformer.toContract(memory.get(chatId)))

    override fun getGlobalMemory(): ResponseEntity<MemoryDocument> = getChatMemory("")

    override fun cancelChatMemoryCollection(chatId: String): ResponseEntity<MemoryDocument> =
        ResponseEntity.ok(transformer.toContract(memory.cancel(chatId)))

    override fun cancelGlobalMemoryCollection(): ResponseEntity<MemoryDocument> = cancelChatMemoryCollection("")

    override fun updateChatMemory(
        chatId: String,
        memoryNotesUpdate: MemoryNotesUpdate,
    ): ResponseEntity<MemoryDocument> = ResponseEntity.ok(
        transformer.toContract(memory.edit(chatId, memoryNotesUpdate.notes, memoryNotesUpdate.revision)),
    )

    override fun updateGlobalMemory(memoryNotesUpdate: MemoryNotesUpdate): ResponseEntity<MemoryDocument> =
        updateChatMemory("", memoryNotesUpdate)

    override fun refreshChatMemory(
        chatId: String,
        memoryRefreshRequest: MemoryRefreshRequest,
    ): ResponseEntity<MemoryDocument> = ResponseEntity.ok(
        transformer.toContract(
            memory.refresh(chatId, memoryRefreshRequest.rebuild, memoryRefreshRequest.immediate),
        ),
    )

    override fun refreshChangedMemory(memoryRefreshRequest: MemoryRefreshRequest?): ResponseEntity<Ok> {
        memory.refreshChanged(memoryRefreshRequest?.rebuild ?: false, memoryRefreshRequest?.immediate ?: false)
        return ResponseEntity.ok(Ok(true))
    }
}
