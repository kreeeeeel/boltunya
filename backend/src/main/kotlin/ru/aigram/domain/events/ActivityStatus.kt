package ru.aigram.domain.events

enum class ActivityStatus(val value: String) {
    QUEUED("queued"),
    GENERATING("generating"),
    SENT("sent"),
    READY("ready"),
    INTERRUPTED("interrupted"),
    CANCELLED("cancelled"),
    ERROR("error"),
}
