package ru.aigram.controller.memory

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.memory.MemoryComponent
import ru.aigram.contract.api.MemoryApi
import ru.aigram.contract.dto.MemoryDocument
import ru.aigram.contract.dto.MemoryNotesUpdate
import ru.aigram.contract.dto.MemoryRefreshRequest
import ru.aigram.contract.dto.Ok
import ru.aigram.transformer.memory.toContract

@RestController
class MemoryController(private val memory: MemoryComponent) : MemoryApi {
    override fun getChatMemory(chatId: String): ResponseEntity<MemoryDocument> =
        ResponseEntity.ok(memory.get(chatId).toContract())

    override fun getGlobalMemory(): ResponseEntity<MemoryDocument> = getChatMemory("")

    override fun cancelChatMemoryCollection(chatId: String): ResponseEntity<MemoryDocument> =
        ResponseEntity.ok(memory.cancel(chatId).toContract())

    override fun cancelGlobalMemoryCollection(): ResponseEntity<MemoryDocument> = cancelChatMemoryCollection("")

    override fun updateChatMemory(
        chatId: String,
        memoryNotesUpdate: MemoryNotesUpdate,
    ): ResponseEntity<MemoryDocument> = ResponseEntity.ok(
        memory.edit(chatId, memoryNotesUpdate.notes, memoryNotesUpdate.revision).toContract(),
    )

    override fun updateGlobalMemory(memoryNotesUpdate: MemoryNotesUpdate): ResponseEntity<MemoryDocument> =
        updateChatMemory("", memoryNotesUpdate)

    override fun refreshChatMemory(
        chatId: String,
        memoryRefreshRequest: MemoryRefreshRequest,
    ): ResponseEntity<MemoryDocument> = ResponseEntity.ok(
        memory.refresh(chatId, memoryRefreshRequest.rebuild, memoryRefreshRequest.immediate).toContract(),
    )

    override fun refreshChangedMemory(memoryRefreshRequest: MemoryRefreshRequest?): ResponseEntity<Ok> {
        memory.refreshChanged(memoryRefreshRequest?.rebuild ?: false, memoryRefreshRequest?.immediate ?: false)
        return ResponseEntity.ok(Ok(true))
    }
}
