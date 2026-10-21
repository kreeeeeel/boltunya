package ru.aigram.component.activity

import org.springframework.stereotype.Component
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.activity.ActivityHistoryStore
import ru.aigram.domain.events.ActivityFeedbackUpdate
import ru.aigram.domain.events.ActivityHistorySettings
import ru.aigram.telegram.TelegramGateway
import ru.aigram.validation.InputValidation
import ru.aigram.domain.events.ActivityFeedback as DomainActivityFeedback
import ru.aigram.domain.events.ActivityHistoryStorage as DomainActivityHistoryStorage

@Component
class ActivityComponent(
    private val replies: ReplyCoordinator,
    private val history: ActivityHistoryStore,
    private val telegram: TelegramGateway,
    private val validation: InputValidation,
) {
    fun details(id: String) = replies.details(id)
    fun page(cursor: String?, limit: Int) = replies.historyPage(cursor, limit)
    fun storage() = history.storage(telegram.status().userId)
    fun configure(settings: ActivityHistorySettings): DomainActivityHistoryStorage {
        validation.validate(settings)

        return history.configure(telegram.status().userId, settings)
    }

    fun pause(paused: Boolean) = replies.pauseQueue(paused)
    fun clear() = replies.clearCompleted()
    fun cancel(id: String) = replies.cancel(id)
    fun prioritize(id: String) = replies.prioritize(id)
    fun feedback(id: String, update: ActivityFeedbackUpdate): DomainActivityFeedback {
        validation.validate(update)
        return replies.saveFeedback(id, update)
    }

    fun removeFeedback(id: String) = replies.removeFeedback(id)
}
