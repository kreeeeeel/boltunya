package ru.aigram.automation.activity

import ru.aigram.domain.ActivityDetails
import ru.aigram.domain.ActivityEntry
import ru.aigram.exception.impl.ActivityNotFoundException

private const val MAX_COMPLETED_ENTRIES = 100
private const val MAX_HISTORY_CHARACTERS = 8_000_000L

class ActivityLog(private val history: ActivityHistoryStore? = null, private val owner: () -> String = { "" }) {
    private val owners = linkedMapOf<String, String>()
    private val traces = linkedMapOf<String, ReplyTrace>()

    @Synchronized
    fun add(entry: ActivityEntry, trigger: String): ReplyTrace {
        val trace = ReplyTrace(entry, trigger)
        traces[entry.id] = trace
        owners[entry.id] = owner()
        history?.watch(owner(), trace)
        prune()
        return trace
    }

    @Synchronized
    fun summaries(): List<ActivityEntry> = traces.entries.toList().asReversed().stream()
        .filter { owners[it.key] == owner() }.map { it.value.summary() }.toList()

    @Synchronized
    fun details(id: String): ActivityDetails = if (owners[id] == owner() && traces[id] != null) {
        traces.getValue(id).details()
    } else {
        history?.details(owner(), id) ?: throw ActivityNotFoundException()
    }

    @Synchronized
    fun clearCompleted() {
        history?.clear(owner())
        traces.entries.removeIf { owners[it.key] == owner() && !it.value.active() }
        owners.keys.retainAll(traces.keys)
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
        owners.keys.retainAll(retained)
    }
}
