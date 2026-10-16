package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.impl.FlowIdentifierMismatchException
import ru.boltunya.exception.impl.FlowNotFoundException
import ru.boltunya.flows.AutomationFlow
import ru.boltunya.flows.FlowEngine
import ru.boltunya.flows.FlowStore
import ru.boltunya.http.RequestReader
import ru.boltunya.storage.SettingsStore

private const val MAX_TEST_MESSAGE_CHARACTERS = 4096

class FlowEndpoint @Inject constructor(
    private val json: Json,
    private val store: FlowStore,
    private val engine: FlowEngine,
    private val settings: SettingsStore,
    private val replies: ReplyCoordinator,
    private val input: RequestReader,
) {
    fun list(): List<AutomationFlow> = store.list()
    fun save(id: String, body: JsonObject) {
        val flow = json.decodeFromJsonElement<AutomationFlow>(body)
        if (id != flow.id) throw FlowIdentifierMismatchException()
        store.save(flow)
        replies.cancelPending()
    }
    fun delete(id: String) {
        store.delete(id)
        replies.cancelPending()
    }
    fun preview(id: String, body: JsonObject): ReplyResult {
        val flow =
            body["flow"]?.let { json.decodeFromJsonElement<AutomationFlow>(it) }
                ?: store.list().firstOrNull { it.id == id }
                ?: throw FlowNotFoundException()
        if (flow.id != id) throw FlowIdentifierMismatchException()
        val message =
            ChatMessage(
                "preview",
                "preview",
                input.text(body, "text", MAX_TEST_MESSAGE_CHARACTERS),
                false,
                0,
                senderName = "Preview",
            )
        return engine.run(flow, message, ChatRule(model = settings.get().defaultModel), listOf(message)).reply
    }
}
