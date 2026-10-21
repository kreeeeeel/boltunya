package ru.aigram.domain.chat

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class MessageEditEntity(
    @field:Min(0) val offset: Int,
    @field:Min(1) val length: Int,
    val kind: MessageEditEntityKind,
    @field:Min(0) val sourceIndex: Int? = null,
    val typeHint: String = "",
    @field:Size(max = 65536) val url: String = "",
    @field:Size(max = 256) val language: String = "",
)
