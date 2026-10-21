package ru.aigram.transformer.activity

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.Activity
import ru.aigram.contract.dto.ActivityAttempt
import ru.aigram.contract.dto.ActivityDetails
import ru.aigram.contract.dto.ActivityFeedback
import ru.aigram.contract.dto.ActivityHistoryPage
import ru.aigram.contract.dto.ActivityHistoryStorage
import ru.aigram.contract.dto.ActivityMessage
import ru.aigram.contract.dto.ActivityStep
import ru.aigram.contract.dto.NullableActivityFeedbackRating
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.contract.dto.ActivityFeedbackRating as ContractActivityFeedbackRating
import ru.aigram.contract.dto.ActivityFeedbackUpdate as ContractActivityFeedbackUpdate
import ru.aigram.contract.dto.ActivityHistorySettingsInput as ContractActivityHistorySettingsInput
import ru.aigram.contract.dto.ActivityKind as ContractActivityKind
import ru.aigram.domain.events.ActivityAttempt as DomainActivityAttempt
import ru.aigram.domain.events.ActivityDetails as DomainActivityDetails
import ru.aigram.domain.events.ActivityFeedback as DomainActivityFeedback
import ru.aigram.domain.events.ActivityFeedbackRating as DomainActivityFeedbackRating
import ru.aigram.domain.events.ActivityFeedbackUpdate as DomainActivityFeedbackUpdate
import ru.aigram.domain.events.ActivityHistoryPage as DomainActivityHistoryPage
import ru.aigram.domain.events.ActivityHistorySettings as DomainActivityHistorySettings
import ru.aigram.domain.events.ActivityHistoryStorage as DomainActivityHistoryStorage

@Component
class ActivityTransformer {
    fun toContract(value: ActivityEntry): Activity = Activity(
        id = value.id,
        chatId = value.chatId,
        title = value.title,
        status = value.status,
        detail = value.detail,
        date = value.date,
        durationMs = value.durationMs,
        avatarUrl = value.avatarUrl,
        phase = value.phase,
        thinking = value.thinking,
        queuePosition = value.queuePosition.toLong(),
        priority = value.priority,
        inputTokens = value.inputTokens.toLong(),
        outputTokens = value.outputTokens.toLong(),
        attempts = value.attempts.toLong(),
        startedAt = value.startedAt,
        finishedAt = value.finishedAt,
        waitUntil = value.waitUntil,
        preview = value.preview,
        feedbackAvailable = value.feedbackAvailable,
        feedbackRating = value.feedbackRating?.let { NullableActivityFeedbackRating.valueOf(it.name) },
        kind = ContractActivityKind.valueOf(value.kind.name),
    )

    fun toContract(value: DomainActivityDetails): ActivityDetails = ActivityDetails(
        entry = toContract(value.entry),
        trigger = value.trigger,
        response = value.response,
        attempts = value.attempts.stream().map(::toContract).toList(),
        steps = value.steps.stream().map { ActivityStep(it.phase, it.date) }.toList(),
        feedback = value.feedback?.let(::toContract),
    )

    fun toContract(value: DomainActivityAttempt): ActivityAttempt = ActivityAttempt(
        number = value.number.toLong(),
        purpose = value.purpose,
        model = value.model,
        messages = value.messages.stream().map { ActivityMessage(it.role, it.text) }.toList(),
        options = value.options,
        startedAt = value.startedAt,
        response = value.response,
        thinking = value.thinking,
        durationMs = value.durationMs,
        inputTokens = value.inputTokens.toLong(),
        outputTokens = value.outputTokens.toLong(),
        tokensPerSecond = value.tokensPerSecond.toBigDecimal(),
        loadDurationMs = value.loadDurationMs,
        promptDurationMs = value.promptDurationMs,
        finishReason = value.finishReason,
        complete = value.complete,
    )

    fun toContract(value: DomainActivityFeedback): ActivityFeedback = ActivityFeedback(
        ContractActivityFeedbackRating.valueOf(value.rating.name),
        value.comment,
        value.updatedAt,
    )

    fun toContract(value: DomainActivityHistoryPage): ActivityHistoryPage = ActivityHistoryPage(
        value.entries.stream().map(::toContract).toList(),
        value.nextCursor,
        value.hasMore,
        value.total,
        toContract(value.storage),
    )

    fun toContract(value: DomainActivityHistoryStorage): ActivityHistoryStorage = ActivityHistoryStorage(
        value.retentionDays,
        value.maxStorageMb,
        value.bytes,
        value.recording,
        value.errorCode,
    )

    fun toDomain(value: ContractActivityHistorySettingsInput): DomainActivityHistorySettings =
        DomainActivityHistorySettings(value.retentionDays, value.maxStorageMb)

    fun toDomain(value: ContractActivityFeedbackUpdate): DomainActivityFeedbackUpdate = DomainActivityFeedbackUpdate(
        DomainActivityFeedbackRating.valueOf(value.rating.name),
        value.comment,
    )
}
