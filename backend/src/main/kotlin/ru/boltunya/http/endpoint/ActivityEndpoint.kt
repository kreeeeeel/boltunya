package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ActivityFeedback
import ru.boltunya.domain.ActivityFeedbackUpdate
import ru.boltunya.domain.ReplyQueueUpdate
import ru.boltunya.exception.impl.MalformedPayloadException
import ru.boltunya.http.InputValidation

class ActivityEndpoint @Inject constructor(
    private val replies: ReplyCoordinator,
    private val json: Json,
    private val validation: InputValidation,
) {
    fun details(id: String) = replies.details(id)

    fun pause(body: JsonObject) {
        replies.pauseQueue(json.decodeFromJsonElement<ReplyQueueUpdate>(body).paused)
    }

    fun clear() = replies.clearCompleted()
    fun cancel(id: String) = replies.cancel(id)
    fun prioritize(id: String) = replies.prioritize(id)

    fun feedback(id: String, body: JsonObject): ActivityFeedback {
        val comment = body["comment"]
        if (comment != null && (comment !is JsonPrimitive || !comment.isString)) throw MalformedPayloadException()
        val update = json.decodeFromJsonElement<ActivityFeedbackUpdate>(body)
        validation.validate(update)
        return replies.saveFeedback(id, update)
    }

    fun removeFeedback(id: String) = replies.removeFeedback(id)
}
