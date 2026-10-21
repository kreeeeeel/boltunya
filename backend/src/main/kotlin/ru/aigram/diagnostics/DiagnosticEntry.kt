package ru.aigram.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticEntry(
    val id: String,
    val date: Long,
    val firstOccurredAt: Long,
    val source: String,
    val code: String,
    val message: String,
    val remediation: String,
    val exceptionType: String,
    val occurrences: Int = 1,
    val metadata: Map<String, String> = emptyMap(),
)
