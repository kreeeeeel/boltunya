package ru.boltunya.memory

import kotlinx.serialization.Serializable

private const val NOTES_CONTEXT_CHARACTERS = 1000

@Serializable
data class MemoryContext(
    val globalNotes: String = "",
    val chatNotes: String = "",
    val summary: String = "",
    val ownerSummary: String = "",
) {
    fun isEmpty(): Boolean = globalNotes.isBlank() && chatNotes.isBlank() && summary.isBlank() && ownerSummary.isBlank()
    fun limited(budget: Int): MemoryContext {
        val notesBudget = minOf(NOTES_CONTEXT_CHARACTERS, budget / 4)
        val global = globalNotes.take(notesBudget)
        val chat = chatNotes.take(notesBudget)
        val remaining = (budget - global.length - chat.length).coerceAtLeast(0)
        val owner = ownerSummary.take(remaining / 2)
        return MemoryContext(global, chat, summary.take(remaining - owner.length), owner)
    }
}
