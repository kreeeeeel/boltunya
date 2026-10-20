package ru.boltunya.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.domain.ActivityFeedbackRating
import ru.boltunya.domain.ActivityFeedbackUpdate
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ActivityFeedbackStoreTest {
    @TempDir lateinit var directory: Path
    private val clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `persists rating and comment separately from any conversation or prompt`() {
        val store = ActivityFeedbackStore(directory, clock)
        val feedback = store.save("event-id", ActivityFeedbackUpdate(ActivityFeedbackRating.GOOD, "Useful reply"))
        assertEquals(clock.millis(), feedback.updatedAt)
        assertEquals(feedback, ActivityFeedbackStore(directory, clock).get("event-id"))
        val content = Files.readString(directory.resolve("feedback/activity.json"))
        assertFalse(content.contains("chatId"))
        assertFalse(content.contains("messages"))
        assertFalse(content.contains("prompt"))
    }

    @Test
    fun `changing rating preserves an omitted comment and accepts explicit comment clearing`() {
        val store = ActivityFeedbackStore(directory, clock)
        store.save("event-id", ActivityFeedbackUpdate(ActivityFeedbackRating.BAD, "Too long"))
        val changed = store.save("event-id", ActivityFeedbackUpdate(ActivityFeedbackRating.GOOD))
        assertEquals("Too long", changed.comment)
        assertEquals(ActivityFeedbackRating.GOOD, changed.rating)
        assertEquals(
            "",
            store.save("event-id", ActivityFeedbackUpdate(ActivityFeedbackRating.GOOD, "")).comment,
        )
        store.remove("event-id")
        store.remove("event-id")
        assertNull(ActivityFeedbackStore(directory, clock).get("event-id"))
    }
}
