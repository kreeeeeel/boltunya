package ru.boltunya.automation.activity

enum class ActivityStatus(val value: String) {
    QUEUED("queued"),
    GENERATING("generating"),
    SENT("sent"),
    READY("ready"),
    CANCELLED("cancelled"),
    ERROR("error"),
}
