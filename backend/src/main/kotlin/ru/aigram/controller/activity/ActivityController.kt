package ru.aigram.controller.activity

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.aigram.component.activity.ActivityComponent
import ru.aigram.contract.api.ActivityApi
import ru.aigram.contract.dto.ActivityDetails
import ru.aigram.contract.dto.ActivityFeedback
import ru.aigram.contract.dto.ActivityFeedbackUpdate
import ru.aigram.contract.dto.ActivityHistoryPage
import ru.aigram.contract.dto.ActivityHistorySettingsInput
import ru.aigram.contract.dto.ActivityHistoryStorage
import ru.aigram.contract.dto.Ok
import ru.aigram.contract.dto.QueuePauseRequest
import ru.aigram.transformer.activity.ActivityTransformer

@RestController
class ActivityController(private val activity: ActivityComponent, private val transformer: ActivityTransformer) :
    ActivityApi {
    override fun activityDetails(id: String): ResponseEntity<ActivityDetails> =
        ResponseEntity.ok(transformer.toContract(activity.details(id)))

    override fun activityHistory(cursor: String?, limit: Int): ResponseEntity<ActivityHistoryPage> =
        ResponseEntity.ok(transformer.toContract(activity.page(cursor, limit)))

    override fun activityHistorySettings(): ResponseEntity<ActivityHistoryStorage> =
        ResponseEntity.ok(transformer.toContract(activity.storage()))

    override fun configureActivityHistory(
        activityHistorySettings: ActivityHistorySettingsInput,
    ): ResponseEntity<ActivityHistoryStorage> =
        ResponseEntity.ok(transformer.toContract(activity.configure(transformer.toDomain(activityHistorySettings))))

    override fun saveActivityFeedback(
        id: String,
        activityFeedbackUpdate: ActivityFeedbackUpdate,
    ): ResponseEntity<ActivityFeedback> =
        ResponseEntity.ok(transformer.toContract(activity.feedback(id, transformer.toDomain(activityFeedbackUpdate))))

    override fun pauseReplyQueue(queuePauseRequest: QueuePauseRequest): ResponseEntity<Ok> {
        activity.pause(queuePauseRequest.paused)
        return ResponseEntity.ok(Ok(true))
    }

    override fun clearActivity(): ResponseEntity<Ok> {
        activity.clear()
        return ResponseEntity.ok(Ok(true))
    }

    override fun cancelActivity(id: String): ResponseEntity<Ok> {
        activity.cancel(id)
        return ResponseEntity.ok(Ok(true))
    }

    override fun prioritizeActivity(id: String): ResponseEntity<Ok> {
        activity.prioritize(id)
        return ResponseEntity.ok(Ok(true))
    }

    override fun removeActivityFeedback(id: String): ResponseEntity<Ok> {
        activity.removeFeedback(id)
        return ResponseEntity.ok(Ok(true))
    }
}
