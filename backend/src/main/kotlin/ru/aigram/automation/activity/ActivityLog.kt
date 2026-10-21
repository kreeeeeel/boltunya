package ru.aigram.automation.activity

import ru.aigram.domain.ActivityDetails
import ru.aigram.domain.ActivityEntry
import ru.aigram.exception.impl.ActivityNotFoundException

private const val MAX_COMPLETED_ENTRIES = 100
private const val MAX_HISTORY_CHARACTERS = 8_000_000L

class ActivityLog {
    private val traces = linkedMapOf<String, ReplyTrace>()

    @Synchronized
    fun add(entry: ActivityEntry, trigger: String): ReplyTrace {
        val trace = ReplyTrace(entry, trigger)
        traces[entry.id] = trace
        prune()
        return trace
    }

    @Synchronized
    fun summaries(): List<ActivityEntry> = traces.values.toList().asReversed().stream().map { it.summary() }.toList()

    @Synchronized
    fun details(id: String): ActivityDetails = (traces[id] ?: throw ActivityNotFoundException()).details()

    @Synchronized
    fun clearCompleted() {
        traces.entries.removeIf { !it.value.active() }
    }

    @Synchronized
    fun prune() {
        var count = 0
        var characters = 0L
        val retained = traces.entries.toList().asReversed().stream().filter { (_, trace) ->
            if (trace.active()) {
                true
            } else {
                count += 1
                characters += trace.characters()
                count <= MAX_COMPLETED_ENTRIES && characters <= MAX_HISTORY_CHARACTERS
            }
        }.map { it.key }.toList().toSet()
        traces.keys.retainAll(retained)
    }
}
