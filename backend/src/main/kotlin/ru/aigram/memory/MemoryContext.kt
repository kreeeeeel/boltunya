package ru.aigram.memory

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val NOTES_CONTEXT_CHARACTERS = 1000
private const val PARTICIPANT_CONTEXT_CHARACTERS = 2000
private const val MAX_CONTEXT_PARTICIPANTS = 80L

@Serializable
data class MemoryContext(
    val globalNotes: String = "",
    val chatNotes: String = "",
    val summary: String = "",
    val ownerSummary: String = "",
    val participants: List<ParticipantIdentity> = emptyList(),
) {
    fun isEmpty(): Boolean =
        globalNotes.isBlank() && chatNotes.isBlank() && summary.isBlank() && ownerSummary.isBlank() &&
            participants.isEmpty()
    fun limited(budget: Int): MemoryContext {
        val available = budget.coerceAtLeast(0)
        val rosterBudget = minOf(PARTICIPANT_CONTEXT_CHARACTERS, available / 3)
        var rosterCharacters = 2
        val roster = participants.stream().limit(MAX_CONTEXT_PARTICIPANTS).takeWhile {
            val size = Json.encodeToString(it).length + 1
            if (rosterCharacters + size <= rosterBudget) {
                rosterCharacters += size
                true
            } else {
                false
            }
        }.toList()
        val rest = available - if (roster.isEmpty()) 0 else rosterCharacters
        val notesBudget = minOf(NOTES_CONTEXT_CHARACTERS, rest / 4)
        val global = globalNotes.take(notesBudget)
        val chat = chatNotes.take(notesBudget)
        val remaining = (rest - global.length - chat.length).coerceAtLeast(0)
        val owner = ownerSummary.take(remaining / 2)
        return MemoryContext(global, chat, summary.take(remaining - owner.length), owner, roster)
    }
}
