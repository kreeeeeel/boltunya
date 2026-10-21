package ru.boltunya.domain

import kotlinx.serialization.Serializable

@Serializable
data class PreviewJob(val id: String, val status: String, val result: ReplyResult? = null, val error: String = "")
