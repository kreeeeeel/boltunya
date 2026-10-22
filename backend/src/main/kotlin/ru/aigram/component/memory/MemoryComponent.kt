package ru.aigram.component.memory

import org.springframework.stereotype.Component
import ru.aigram.domain.memory.MemoryDocument
import ru.aigram.domain.memory.MemoryNotesUpdate
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.MemoryNotReadyException
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.memory.MemoryStore
import ru.aigram.telegram.TelegramGateway
import ru.aigram.validation.InputValidation

@Component
class MemoryComponent(
    private val memory: MemoryCoordinator,
    private val store: MemoryStore,
    private val telegram: TelegramGateway,
    private val validation: InputValidation,
) {
    fun get(chatId: String): MemoryDocument {
        access(chatId)
        return memory.document(chatId)
    }

    fun edit(chatId: String, notes: String, revision: String): MemoryDocument {
        val ownerId = access(chatId)
        val update = MemoryNotesUpdate(notes, revision)
        validation.validate(update)

        validateOwner(ownerId)
        store.edit(chatId, update.notes, update.revision, ownerId)
        val document = memory.document(chatId)
        validateOwner(ownerId)
        return document
    }

    fun refresh(chatId: String, rebuild: Boolean, immediate: Boolean): MemoryDocument {
        access(chatId)
        memory.refresh(chatId, rebuild, immediate)
        return memory.document(chatId)
    }

    fun refreshChanged(rebuild: Boolean, immediate: Boolean) = memory.refresh(rebuild = rebuild, immediate = immediate)

    fun cancel(chatId: String): MemoryDocument {
        access(chatId)
        memory.cancel(chatId)
        return memory.document(chatId)
    }

    private fun access(chatId: String): String {
        if (chatId.isEmpty()) {
            return ""
        }
        val account = telegram.status()
        if (account.state != "ready" || account.userId.isEmpty()) {
            throw MemoryNotReadyException()
        }
        if (telegram.chats().none { it.id == chatId }) {
            throw ChatNotFoundException()
        }
        store.activate(account.userId)
        return account.userId
    }

    private fun validateOwner(ownerId: String) {
        if (ownerId.isEmpty()) {
            return
        }
        val account = telegram.status()
        if (account.state != "ready" || account.userId != ownerId) {
            throw MemoryNotReadyException()
        }
    }
}
