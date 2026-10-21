package ru.aigram.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class OutgoingMediaKind {
    @SerialName("media")
    MEDIA,

    @SerialName("photo")
    PHOTO,

    @SerialName("video")
    VIDEO,

    @SerialName("document")
    DOCUMENT,
}
