package ru.aigram.storage

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.ActivityFeedback
import ru.aigram.domain.ActivityFeedbackUpdate
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock

/** Explicit local human feedback. No conversation, generation trace or training data is copied here. */
@Component
class ActivityFeedbackStore @Inject constructor(directory: Path, private val clock: Clock) {
    private val file = directory.resolve("feedback/activity.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var entries = if (Files.exists(file)) {
        json.decodeFromString<Map<String, ActivityFeedback>>(Files.readString(file))
    } else {
        emptyMap()
    }

    fun get(id: String): ActivityFeedback? = entries[id]

    @Synchronized
    fun save(id: String, update: ActivityFeedbackUpdate): ActivityFeedback {
        val feedback = ActivityFeedback(update.rating, update.comment ?: entries[id]?.comment.orEmpty(), clock.millis())
        persist(entries + (id to feedback))
        return feedback
    }

    @Synchronized
    fun remove(id: String) {
        if (id in entries) persist(entries - id)
    }

    private fun persist(next: Map<String, ActivityFeedback>) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("activity.tmp")
        Files.writeString(temporary, json.encodeToString(next))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        entries = next
    }
}
