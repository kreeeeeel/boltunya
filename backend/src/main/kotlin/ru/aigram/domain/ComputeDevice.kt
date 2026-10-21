package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class ComputeDevice(
    val id: String,
    val name: String,
    val kind: String,
    val memoryBytes: Long = 0,
    val selectable: Boolean = true,
    val reason: String = "",
)
