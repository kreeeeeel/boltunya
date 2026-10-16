package ru.boltunya.automation.activity

enum class ActivityPhase(val value: String) {
    QUEUED("queued"),
    CONTEXT("context"),
    RESEARCH("research"),
    SEARCH("search"),
    REPLY("reply"),
    RETRY("retry"),
    VALIDATING("validating"),
    SENDING("sending"),
    SENT("sent"),
    CANCELLED("cancelled"),
    ERROR("error"),
}
