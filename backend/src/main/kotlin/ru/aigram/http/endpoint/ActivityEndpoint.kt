package ru.aigram.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.activity.ActivityHistoryStore
import ru.aigram.domain.ActivityFeedback
import ru.aigram.domain.ActivityFeedbackUpdate
import ru.aigram.domain.ActivityHistorySettings
import ru.aigram.domain.ActivityHistoryStorage
import ru.aigram.domain.ReplyQueueUpdate
import ru.aigram.exception.impl.MalformedPayloadException
import ru.aigram.http.InputValidation
import ru.aigram.telegram.TelegramGateway

class ActivityEndpoint @Inject constructor(
    private val replies: ReplyCoordinator,
    private val json: Json,
    private val validation: InputValidation,
    private val history: ActivityHistoryStore,
    private val telegram: TelegramGateway,
) {
    fun history(cursor: String?, limit: Int) = replies.historyPage(cursor, limit)
    fun storage() = history.storage(telegram.status().userId)
    fun configure(body: JsonObject): ActivityHistoryStorage {
        val settings = json.decodeFromJsonElement<ActivityHistorySettings>(body)
        validation.validate(settings)
        return history.configure(telegram.status().userId, settings)
    }

    fun close() = history.close()

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
