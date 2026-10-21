package ru.aigram.domain.events.diagnostics

import kotlinx.serialization.Serializable

@Serializable
data class DesktopUpdateFailure(val failedAt: String, val stage: String, val code: String)
