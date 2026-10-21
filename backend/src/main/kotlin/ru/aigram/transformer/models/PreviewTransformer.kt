package ru.aigram.transformer.models

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.PreviewJob
import ru.aigram.contract.dto.PreviewJobStatus
import ru.aigram.contract.dto.Reply
import ru.aigram.domain.ai.PreviewJob as DomainPreviewJob
import ru.aigram.domain.ai.ReplyResult as DomainReplyResult

@Component
class PreviewTransformer {
    fun toContract(value: DomainReplyResult): Reply = Reply(
        value.text,
        value.durationMs,
        value.tokensPerSecond.toBigDecimal(),
        value.model,
        value.stickerId,
    )

    fun toContract(value: DomainPreviewJob): PreviewJob = PreviewJob(
        value.id,
        PreviewJobStatus.forValue(value.status),
        value.result?.let(::toContract),
        value.error,
    )
}
