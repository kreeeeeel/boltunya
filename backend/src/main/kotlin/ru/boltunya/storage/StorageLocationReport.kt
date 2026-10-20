package ru.boltunya.storage

import kotlinx.serialization.Serializable

@Serializable
data class StorageLocationReport(val locations: List<StorageLocation>)
