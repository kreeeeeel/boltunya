package ru.boltunya.ai

import jakarta.inject.Inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class EngineProcessLocator @Inject constructor() {
    fun find(executable: Path): ProcessHandle? = ProcessHandle.allProcesses().use { processes ->
        processes.filter { process ->
            process.info().command().map { matches(Path.of(it), executable) }.orElse(false) &&
                process.parent().map { parent ->
                    parent.info().command().map { !matches(Path.of(it), executable) }.orElse(true)
                }.orElse(true)
        }.findFirst().orElse(null)
    }

    internal fun matches(actual: Path, expected: Path): Boolean = try {
        // Windows Store virtualization can expose two paths for the same executable.
        Files.isSameFile(actual, expected)
    } catch (_: IOException) {
        // The process or its executable may disappear while enumerating processes.
        false
    }
}
