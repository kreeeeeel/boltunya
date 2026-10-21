package ru.boltunya.storage

import com.sun.jna.platform.win32.Crypt32Util
import jakarta.inject.Inject
import ru.boltunya.exception.impl.InvalidSearchKeyException
import ru.boltunya.validation.InputRule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong

private const val KEY_FILE = "search.credentials"

class SearchCredentialStore @Inject constructor(directory: Path) {
    private val file = directory.resolve(KEY_FILE)
    private val revision = AtomicLong()

    fun exists(): Boolean = Files.exists(file)

    fun revision(): Long = revision.get()

    @Synchronized
    fun save(value: String) {
        val key = value.trim()
        if (!InputRule.SEARCH_KEY.accepts(key)) throw InvalidSearchKeyException()
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("$KEY_FILE.tmp")
        Files.write(temporary, Crypt32Util.cryptProtectData(key.toByteArray(Charsets.UTF_8)))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        revision.incrementAndGet()
    }

    @Synchronized
    fun load(): String = if (exists()) {
        String(Crypt32Util.cryptUnprotectData(Files.readAllBytes(file)), Charsets.UTF_8)
    } else {
        ""
    }

    @Synchronized
    fun delete() {
        Files.deleteIfExists(file)
        revision.incrementAndGet()
    }
}
