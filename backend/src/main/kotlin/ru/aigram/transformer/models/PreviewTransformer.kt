package ru.aigram.transformer.models

import ru.aigram.contract.dto.PreviewJob
import ru.aigram.contract.dto.PreviewJobStatus
import ru.aigram.contract.dto.Reply
import ru.aigram.transformer.chat.toContract
import ru.aigram.domain.ai.PreviewJob as DomainPreviewJob
import ru.aigram.domain.ai.ReplyResult as DomainReplyResult

fun DomainReplyResult.toContract(): Reply = Reply(
    text,
    durationMs,
    tokensPerSecond.toBigDecimal(),
    model,
    stickerId,
    entities.stream().map { it.toContract() }.toList(),
)

fun DomainPreviewJob.toContract(): PreviewJob = PreviewJob(
    id,
    PreviewJobStatus.forValue(status),
    result?.toContract(),
    error,
)
