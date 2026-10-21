package ru.aigram.domain.ai

import kotlinx.serialization.Serializable

@Serializable
data class ComputeDeviceStatus(
    val configured: String,
    val active: String?,
    val managed: Boolean,
    val selectionSupported: Boolean,
    val restartRequired: Boolean,
    val limitation: String,
    val devices: List<ComputeDevice>,
    val autoUsesEnvironment: Boolean = true,
    val nvidiaSelectionOnly: Boolean = true,
)
