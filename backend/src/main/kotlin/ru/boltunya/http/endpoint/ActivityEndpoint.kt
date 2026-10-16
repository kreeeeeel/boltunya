package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ReplyQueueUpdate

class ActivityEndpoint @Inject constructor(private val replies: ReplyCoordinator, private val json: Json) {
    fun details(id: String) = replies.details(id)

    fun pause(body: JsonObject) {
        replies.pauseQueue(json.decodeFromJsonElement<ReplyQueueUpdate>(body).paused)
    }

    fun clear() = replies.clearCompleted()
    fun cancel(id: String) = replies.cancel(id)
    fun prioritize(id: String) = replies.prioritize(id)
}
