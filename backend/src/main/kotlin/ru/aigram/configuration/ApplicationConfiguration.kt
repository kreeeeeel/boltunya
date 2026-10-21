package ru.aigram.configuration

import java.nio.file.Path

data class ApplicationConfiguration(
    val directory: Path,
    val startup: Boolean,
    val ollamaUrl: String = System.getenv("BOLTUNYA_OLLAMA_URL")
        ?: "http://127.0.0.1:11434",
)
