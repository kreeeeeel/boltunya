package ru.aigram.automation.activity

enum class ActivityPhase(val value: String) {
    QUEUED("queued"),
    CONTEXT("context"),
    MEMORY("memory"),
    WAITING_RESOURCE("waiting-resource"),
    RESEARCH("research"),
    SEARCH("search"),
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
