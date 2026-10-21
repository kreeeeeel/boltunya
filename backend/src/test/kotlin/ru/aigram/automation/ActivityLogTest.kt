package ru.aigram.automation

import org.junit.jupiter.api.Test
import ru.aigram.automation.activity.ActivityLog
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ActivityStatus
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityLogTest {
    @Test
    fun `retention evicts oldest completed events while preserving active requests`() {
        val log = ActivityLog()
        log.add(ActivityEntry("active", "42", "Test", "queued", "model", 0), "Waiting")
        IntStream.range(0, 110).forEach { index ->
            log.add(ActivityEntry(index.toString(), "43", "Test", "queued", "model", 0), "Message")
                .finish(ActivityStatus.SENT, "model", "Reply")
        }
        log.prune()
        assertEquals(101, log.summaries().size)
        assertTrue(log.summaries().any { it.id == "active" })
        assertTrue(log.summaries().none { it.id == "0" })
        log.clearCompleted()
        assertEquals("active", log.summaries().single().id)
    }

    @Test
    fun `feedback availability follows a final ready response and excludes in-flight or empty results`() {
        val log = ActivityLog()
        val trace = log.add(ActivityEntry("ready", "42", "Test", "queued", "model", 0), "Trigger")
        assertFalse(log.details("ready").entry.feedbackAvailable)
        trace.start()
        trace.generated("Ready answer")
        assertFalse(log.details("ready").entry.feedbackAvailable)
        trace.finish(ActivityStatus.SENT, "model", "Ready answer")
        assertTrue(log.details("ready").entry.feedbackAvailable)
        val failed = log.add(ActivityEntry("failed", "42", "Test", "queued", "model", 0), "Trigger")
        failed.finish(ActivityStatus.ERROR, "Error")
        assertFalse(log.details("failed").entry.feedbackAvailable)
    }
}
