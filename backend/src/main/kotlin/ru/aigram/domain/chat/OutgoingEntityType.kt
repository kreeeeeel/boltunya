package ru.aigram.domain.chat

enum class OutgoingEntityType(val value: String) {
    BOLD("bold"),
    ITALIC("italic"),
    UNDERLINE("underline"),
    STRIKE("strike"),
    SPOILER("spoiler"),
    CODE("code"),
    PRE("pre"),
    PRECODE("precode"),
    TEXTURL("texturl"),
    QUOTE("quote"),
    EXPANDABLEQUOTE("expandablequote"),
    ;

    fun monospaced(): Boolean = this == CODE || this == PRE || this == PRECODE

    fun quotation(): Boolean = this == QUOTE || this == EXPANDABLEQUOTE
}
