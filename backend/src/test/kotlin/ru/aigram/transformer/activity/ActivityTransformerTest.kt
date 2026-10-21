package ru.aigram.transformer.activity

import org.junit.jupiter.api.Test
import ru.aigram.contract.dto.ActivityFeedbackRating
import ru.aigram.contract.dto.ActivityFeedbackUpdate
import ru.aigram.contract.dto.ActivityKind
import ru.aigram.domain.events.ActivityAttempt
import ru.aigram.domain.events.ActivityDetails
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ActivityFeedback
import ru.aigram.domain.events.ActivityMessage
import ru.aigram.domain.events.ActivityStep
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ru.aigram.domain.events.ActivityFeedbackRating as DomainFeedbackRating
import ru.aigram.domain.events.ActivityKind as DomainActivityKind

class ActivityTransformerTest {
    private val transformer = ActivityTransformer()

    @Test
    fun `event details preserve full timestamps reasoning traces and human feedback`() {
        val timestamp = 4_000_000_000L
        val value = ActivityDetails(
            ActivityEntry(
                "event-1",
                "-1009007199254740993",
                "Synthetic chat",
                "ready",
                "",
                timestamp,
                durationMs = 3_000_000_000L,
                kind = DomainActivityKind.DRAFT,
            ),
            trigger = "Synthetic question",
            response = "Synthetic answer",
            attempts = listOf(
                ActivityAttempt(
                    1, "reply", "synthetic-model", listOf(ActivityMessage("user", "Synthetic input")),
                    "{}", timestamp, response = "Synthetic answer", thinking = "Synthetic reasoning",
                    inputTokens = 73, outputTokens = 19, tokensPerSecond = 12.5, complete = true,
                ),
            ),
            steps = listOf(ActivityStep("queued", timestamp), ActivityStep("ready", timestamp + 1)),
            feedback = ActivityFeedback(DomainFeedbackRating.GOOD, "Useful", timestamp),
        )

        val mapped = transformer.toContract(value)

        assertEquals(timestamp, mapped.entry.date)
        assertEquals(3_000_000_000L, mapped.entry.durationMs)
        assertEquals(value.entry.chatId, mapped.entry.chatId)
        assertEquals(ActivityKind.DRAFT, mapped.entry.kind)
        assertEquals("Synthetic answer", mapped.response)
        assertEquals("Synthetic reasoning", mapped.attempts.single().thinking)
        assertEquals("Synthetic input", mapped.attempts.single().messages.single().text)
        assertEquals(BigDecimal("12.5"), mapped.attempts.single().tokensPerSecond)
        assertEquals(listOf("queued", "ready"), mapped.steps.stream().map { it.phase }.toList())
        assertEquals(ActivityFeedbackRating.GOOD, mapped.feedback?.rating)
        assertEquals("Useful", mapped.feedback?.comment)
    }

    @Test
    fun `omitted feedback comment stays distinct from an explicit empty comment`() {
        assertNull(transformer.toDomain(ActivityFeedbackUpdate(ActivityFeedbackRating.BAD)).comment)
        assertEquals("", transformer.toDomain(ActivityFeedbackUpdate(ActivityFeedbackRating.GOOD, "")).comment)
    }
}
