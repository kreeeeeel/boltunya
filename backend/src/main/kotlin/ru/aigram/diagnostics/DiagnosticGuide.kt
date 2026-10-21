package ru.aigram.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticGuide(val message: String, val remediation: String)
