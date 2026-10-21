package ru.aigram.domain.chat

import jakarta.validation.Valid
import jakarta.validation.constraints.Size

data class MessageEditRequest(
    @field:Size(max = 65536) val text: String,
    @field:Valid @field:Size(max = 1024) val entities: List<MessageEditEntity>,
    @field:Size(min = 64, max = 64) val revision: String,
)
