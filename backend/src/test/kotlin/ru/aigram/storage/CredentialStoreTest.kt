package ru.aigram.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.TelegramCredentials
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CredentialStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `encrypts keys for the current Windows account and deletes them on logout`() {
        val store = CredentialStore(directory)
        val credentials = TelegramCredentials(12345, "a".repeat(32), "+79990000000")
        store.save(credentials)
        assertEquals(credentials, store.load())
        assertFalse(String(Files.readAllBytes(directory.resolve("telegram.credentials"))).contains(credentials.apiHash))
        store.delete()
        assertNull(store.load())
    }
}
