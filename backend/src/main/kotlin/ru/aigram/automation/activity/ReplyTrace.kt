package ru.aigram.automation.activity

import org.springframework.ai.chat.messages.Message
import ru.aigram.domain.ActivityAttempt
import ru.aigram.domain.ActivityDetails
import ru.aigram.domain.ActivityEntry
import ru.aigram.domain.ActivityKind
import ru.aigram.domain.ActivityMessage
import ru.aigram.domain.ActivityStep

private val FINAL_STATUSES = setOf(
    ActivityStatus.SENT.value,
    ActivityStatus.READY.value,
    ActivityStatus.CANCELLED.value,
    ActivityStatus.ERROR.value,
)

class ReplyTrace(private var entry: ActivityEntry, private var trigger: String) {
    private val attempts = mutableListOf<AttemptTrace>()
    private val steps = mutableListOf(ActivityStep("queued", System.currentTimeMillis()))
    private var response = ""

    @Synchronized
    fun prioritize() {
        entry = entry.copy(priority = true)
    }

    @Synchronized
    fun foregroundMemory(): Boolean = entry.kind == ActivityKind.MEMORY && entry.priority

    @Synchronized
    fun currentPhase(): ActivityPhase =
        ActivityPhase.entries.firstOrNull { it.value == entry.phase } ?: ActivityPhase.CONTEXT

    @Synchronized
    fun updateTrigger(text: String) {
        if (active()) trigger = text
    }

    @Synchronized
    fun phase(value: ActivityPhase) {
        if (entry.phase == value.value || !active()) return
        entry = entry.copy(phase = value.value)
        steps.add(ActivityStep(value.value, System.currentTimeMillis()))
    }

    @Synchronized
    fun start() {
        if (!active()) return
        entry = entry.copy(status = ActivityStatus.GENERATING.value, startedAt = System.currentTimeMillis())
        phase(ActivityPhase.CONTEXT)
    }

    @Synchronized
    fun begin(model: String, messages: List<Message>, options: String): AttemptTrace {
        val retainContent = entry.kind != ActivityKind.MEMORY
        val attempt = AttemptTrace(
            ActivityAttempt(
                attempts.size + 1,
                entry.phase,
                model,
                if (retainContent) {
                    messages.stream().map {
                        ActivityMessage(it.messageType.value, it.text.orEmpty())
                    }.toList()
                } else {
                    emptyList()
                },
                options,
                System.currentTimeMillis(),
            ),
            retainContent,
        )
        attempts.add(attempt)
        entry = entry.copy(detail = model)
        return attempt
    }

    @Synchronized
    fun generated(text: String) {
        if (active() && entry.kind != ActivityKind.MEMORY) response = text
    }

    @Synchronized
    fun finish(status: ActivityStatus, detail: String, text: String? = null) {
        if (!active()) return
        phase(ActivityPhase.valueOf(status.name))
        if (text != null && entry.kind != ActivityKind.MEMORY) response = text
        val now = System.currentTimeMillis()
        attempts.stream().forEach { it.stop(now) }
        entry = entry.copy(
            status = status.value,
            detail = detail,
            finishedAt = now,
            durationMs = if (entry.startedAt > 0) now - entry.startedAt else 0,
        )
    }

    @Synchronized
    fun active(): Boolean =
        entry.status == ActivityStatus.QUEUED.value || entry.status == ActivityStatus.GENERATING.value

    @Synchronized
    fun summary(): ActivityEntry {
        val usage = attempts.stream().map { it.usage() }.toList()
        return entry.copy(
            thinking = if (entry.phase == ActivityPhase.REPLY.value || entry.phase == ActivityPhase.RESEARCH.value ||
                entry.phase == ActivityPhase.RETRY.value
            ) {
                attempts.lastOrNull()?.thinkingPreview().orEmpty()
            } else {
                ""
            },
            inputTokens = usage.stream().mapToInt { it.first }.sum(),
            outputTokens = usage.stream().mapToInt { it.second }.sum(),
            attempts = attempts.size,
            preview = response.ifBlank { trigger }.take(180),
            feedbackAvailable = entry.status in FINAL_STATUSES && response.isNotBlank(),
            durationMs = if (entry.startedAt > 0 &&
                active()
            ) {
                System.currentTimeMillis() - entry.startedAt
            } else {
                entry.durationMs
            },
        )
    }

    @Synchronized
    fun details(): ActivityDetails = ActivityDetails(
        summary(),
        trigger,
        response,
        attempts.stream().map { it.snapshot() }.toList(),
        steps.toList(),
    )

    @Synchronized
    fun characters(): Long = trigger.length + response.length + attempts.stream().mapToLong { it.characters() }.sum()
}
