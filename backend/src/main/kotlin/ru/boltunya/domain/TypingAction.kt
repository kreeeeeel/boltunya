package ru.boltunya.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TypingAction {
    @SerialName("typing")
    TYPING,

    @SerialName("uploadPhoto")
    UPLOAD_PHOTO,

    @SerialName("uploadVideo")
    UPLOAD_VIDEO,

    @SerialName("uploadDocument")
    UPLOAD_DOCUMENT,

    @SerialName("recordVoice")
    RECORD_VOICE,

    @SerialName("uploadVoice")
    UPLOAD_VOICE,

    @SerialName("recordVideo")
    RECORD_VIDEO,

    @SerialName("uploadVideoNote")
    UPLOAD_VIDEO_NOTE,

    @SerialName("chooseSticker")
    CHOOSE_STICKER,
}
