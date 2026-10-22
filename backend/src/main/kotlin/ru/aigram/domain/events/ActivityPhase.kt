package ru.aigram.domain.events

enum class ActivityPhase(val value: String) {
    QUEUED("queued"),
    CONTEXT("context"),
    TRANSCRIBING("transcribing"),
    SYNTHESIZING("synthesizing"),
    MEMORY("memory"),
    WAITING_RESOURCE("waiting-resource"),
    REPLY("reply"),
    RETRY("retry"),
    VALIDATING("validating"),
    SENDING("sending"),
    WAITING_DELIVERY("waiting-delivery"),
    SENT("sent"),
    READY("ready"),
    INTERRUPTED("interrupted"),
    CANCELLED("cancelled"),
    ERROR("error"),
}
