package ru.aigram.domain.events

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import kotlinx.serialization.Serializable

@Serializable
data class ActivityHistorySettings(
    @field:Min(0) @field:Max(36500) val retentionDays: Int = 0,
    @field:Min(0) @field:Max(1048576) val maxStorageMb: Int = 0,
)
