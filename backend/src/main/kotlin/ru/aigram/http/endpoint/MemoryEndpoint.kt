package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.MalformedPayloadException
import ru.aigram.exception.impl.MemoryNotReadyException
import ru.aigram.http.InputValidation
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.memory.MemoryDocument
import ru.aigram.memory.MemoryNotesUpdate
import ru.aigram.memory.MemoryRefreshField
import ru.aigram.memory.MemoryRefreshRequest
import ru.aigram.memory.MemoryStore
import ru.aigram.telegram.TelegramGateway

class MemoryEndpoint @Inject constructor(
    private val memory: MemoryCoordinator,
    private val store: MemoryStore,
    private val telegram: TelegramGateway,
    private val json: Json,
    private val validation: InputValidation,
) {
    fun get(chatId: String): MemoryDocument {
        access(chatId)
        return memory.document(chatId)
    }

    fun edit(chatId: String, body: JsonObject): MemoryDocument {
        access(chatId)
        val update = json.decodeFromJsonElement<MemoryNotesUpdate>(body)
        validation.validate(update)
        store.edit(chatId, update.notes, update.revision)
        return memory.document(chatId)
    }

    fun refresh(chatId: String, body: JsonObject): MemoryDocument {
        access(chatId)
        val request = request(body)
        memory.refresh(chatId, request.rebuild, request.immediate)
        return memory.document(chatId)
    }

    fun refreshChanged(body: JsonObject) {
        val request = request(body)
        memory.refresh(rebuild = request.rebuild, immediate = request.immediate)
    }

    fun cancel(chatId: String): MemoryDocument {
        access(chatId)
        memory.cancel(chatId)
        return memory.document(chatId)
    }

    private fun request(body: JsonObject): MemoryRefreshRequest {
        body.entries.stream().forEach { (name, value) ->
            val field = MemoryRefreshField.entries.firstOrNull { it.jsonName == name }
            if (field == null || !field.accepts(value)) throw MalformedPayloadException()
        }
        return json.decodeFromJsonElement(body)
    }

    private fun access(chatId: String) {
        if (chatId.isEmpty()) return
        val account = telegram.status()
        if (account.state != "ready" || account.userId.isEmpty()) throw MemoryNotReadyException()
        if (telegram.chats().none { it.id == chatId }) throw ChatNotFoundException()
        store.activate(account.userId)
    }
}
