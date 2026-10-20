package ru.boltunya.memory

internal const val FACTUAL_MEMORY_VERSION = 1

data class StoredMemory(
    val notes: String,
    val summary: String,
    val revision: String,
    val documentRevision: String,
    val factsVersion: Int = 0,
) {
    fun facts(): String = if (factsVersion == FACTUAL_MEMORY_VERSION) summary else ""
}
