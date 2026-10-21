package ru.aigram.client

data class RecordedClientRequest(
    val method: String,
    val path: String,
    val query: String?,
    val cookie: String,
    val body: String,
    val bytes: ByteArray,
    val contentType: String,
    val fileName: String,
)
