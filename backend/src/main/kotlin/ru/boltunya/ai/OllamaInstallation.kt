package ru.boltunya.ai

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

private const val OLLAMA_EXECUTABLE = "ollama.exe"

class OllamaInstallation(private val directory: Path, private val environment: Map<String, String> = System.getenv()) {
    fun executable(): Path? {
        val locations = listOf(directory.resolve("engine").resolve(OLLAMA_EXECUTABLE)) +
            listOfNotNull(
                variable("LOCALAPPDATA")?.let { Path.of(it, "Programs", "Ollama", OLLAMA_EXECUTABLE) },
                variable("USERPROFILE")?.let {
                    Path.of(it, "AppData", "Local", "Programs", "Ollama", OLLAMA_EXECUTABLE)
                },
                variable("ProgramFiles")?.let { Path.of(it, "Ollama", OLLAMA_EXECUTABLE) },
                variable("ProgramW6432")?.let { Path.of(it, "Ollama", OLLAMA_EXECUTABLE) },
            ) + variable("PATH").orEmpty().split(File.pathSeparator).stream()
                .map { it.trim().trim('"') }
                .filter { it.isNotBlank() }
                .map { Path.of(it, OLLAMA_EXECUTABLE) }
                .toList()
        return locations.stream().map { it.toAbsolutePath().normalize() }
            .filter { Files.isRegularFile(it) }.findFirst().orElse(null)
    }

    private fun variable(name: String): String? = environment.entries.stream()
        .filter { it.key.equals(name, ignoreCase = true) }.map { it.value }.findFirst().orElse(null)
        ?.takeIf { it.isNotBlank() }
}
