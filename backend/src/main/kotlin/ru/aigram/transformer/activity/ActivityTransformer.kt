package ru.aigram.transformer.activity

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

fun ActivityEntry.toContract(): Activity = Activity(
    id = id,
    chatId = chatId,
    title = title,
    status = status,
    detail = detail,
    date = date,
    durationMs = durationMs,
    avatarUrl = avatarUrl,
    phase = phase,
    thinking = thinking,
    queuePosition = queuePosition.toLong(),
    priority = priority,
    inputTokens = inputTokens.toLong(),
    outputTokens = outputTokens.toLong(),
    attempts = attempts.toLong(),
    startedAt = startedAt,
    finishedAt = finishedAt,
    waitUntil = waitUntil,
    preview = preview,
    feedbackAvailable = feedbackAvailable,
    feedbackRating = feedbackRating?.let { NullableActivityFeedbackRating.valueOf(it.name) },
    kind = ContractActivityKind.valueOf(kind.name),
)

fun DomainActivityDetails.toContract(): ActivityDetails = ActivityDetails(
    entry = entry.toContract(),
    trigger = trigger,
    response = response,
    attempts = attempts.stream().map { it.toContract() }.toList(),
    steps = steps.stream().map { ActivityStep(it.phase, it.date) }.toList(),
    feedback = feedback?.toContract(),
)

fun DomainActivityAttempt.toContract(): ActivityAttempt = ActivityAttempt(
    number = number.toLong(),
    purpose = purpose,
    model = model,
    messages = messages.stream().map { ActivityMessage(it.role, it.text) }.toList(),
    options = options,
    startedAt = startedAt,
    response = response,
    thinking = thinking,
    durationMs = durationMs,
    inputTokens = inputTokens.toLong(),
    outputTokens = outputTokens.toLong(),
    tokensPerSecond = tokensPerSecond.toBigDecimal(),
    loadDurationMs = loadDurationMs,
    promptDurationMs = promptDurationMs,
    finishReason = finishReason,
    complete = complete,
)

fun DomainActivityFeedback.toContract(): ActivityFeedback = ActivityFeedback(
    ContractActivityFeedbackRating.valueOf(rating.name),
    comment,
    updatedAt,
)

fun DomainActivityHistoryPage.toContract(): ActivityHistoryPage = ActivityHistoryPage(
    entries.stream().map { it.toContract() }.toList(),
    nextCursor,
    hasMore,
    total,
    storage.toContract(),
)

fun DomainActivityHistoryStorage.toContract(): ActivityHistoryStorage = ActivityHistoryStorage(
    retentionDays,
    maxStorageMb,
    bytes,
    recording,
    errorCode,
)

fun ContractActivityHistorySettingsInput.toDomain(): DomainActivityHistorySettings =
    DomainActivityHistorySettings(retentionDays, maxStorageMb)

fun ContractActivityFeedbackUpdate.toDomain(): DomainActivityFeedbackUpdate = DomainActivityFeedbackUpdate(
    DomainActivityFeedbackRating.valueOf(rating.name),
    comment,
)
