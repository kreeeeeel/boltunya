package ru.aigram.domain

import kotlinx.serialization.Serializable

@Serializable
data class MessageEntity(
    val offset: Int,
    val length: Int,
    val type: String,
    val url: String = "",
    val language: String = "",
)
