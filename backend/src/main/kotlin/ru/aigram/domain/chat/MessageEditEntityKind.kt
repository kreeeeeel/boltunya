package ru.aigram.domain.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageEditEntityKind {
    @SerialName("existing")
    EXISTING,

    @SerialName("bold")
    BOLD,

    @SerialName("italic")
    ITALIC,

    @SerialName("underline")
    UNDERLINE,

    @SerialName("strike")
    STRIKE,

    @SerialName("code")
    CODE,

    @SerialName("pre")
    PRE,

    @SerialName("precode")
    PRECODE,

    @SerialName("texturl")
    TEXTURL,

    @SerialName("spoiler")
    SPOILER,

    @SerialName("quote")
    QUOTE,

    @SerialName("expandablequote")
    EXPANDABLEQUOTE,
}
