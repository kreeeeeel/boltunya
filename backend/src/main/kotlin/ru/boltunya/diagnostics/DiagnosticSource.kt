package ru.boltunya.diagnostics

enum class DiagnosticSource(val value: String) {
    REPLY("reply"),
    ENGINE("engine"),
    MEMORY("memory"),
    HTTP("http"),
    UPDATE("update"),
}
