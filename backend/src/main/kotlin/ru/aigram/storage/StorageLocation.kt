package ru.aigram.storage

import kotlinx.serialization.Serializable

@Serializable
data class StorageLocation(
    val id: String,
    val label: String,
    val path: String,
    val available: Boolean,
    val openable: Boolean,
    val kind: String = "directory",
    val note: String = "",
)
