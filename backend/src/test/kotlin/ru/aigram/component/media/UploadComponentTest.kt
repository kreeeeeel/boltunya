package ru.aigram.component.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.domain.media.OutgoingMediaKind
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.exception.impl.InvalidUploadException
import ru.aigram.exception.impl.TelegramNotConnectedException
import ru.aigram.exception.impl.UploadNotFoundException
import ru.aigram.media.UploadStore
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UploadComponentTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `stores raw binary bodies and decodes the filename header exactly once`() {
        val store = UploadStore(directory, Clock.systemUTC())
        val component = UploadComponent(store, FakeTelegramGateway())
        val bytes = byteArrayOf(0, 1, 13, 10, -1, -128)

        val result = component.create("%D1%84%D0%BE%D1%82%D0%BE%2B1.png", "image/png", bytes, bytes.size.toLong())

        assertEquals("фото+1.png", result.fileName)
        assertContentEquals(bytes, Files.readAllBytes(store.media("100", result.id, OutgoingMediaKind.PHOTO, "").path))
        component.delete(result.id)
        assertFailsWith<UploadNotFoundException> { store.media("100", result.id, OutgoingMediaKind.PHOTO, "") }
    }

    @Test
    fun `rejects malformed names and incomplete binary bodies`() {
        val component = UploadComponent(UploadStore(directory, Clock.systemUTC()), FakeTelegramGateway())

        assertFailsWith<InvalidUploadException> { component.create("bad%ZZ.png", "image/png", byteArrayOf(1), 1) }
        assertFailsWith<InvalidUploadException> { component.create("photo.png", "image/png", byteArrayOf(1), 2) }
    }

    @Test
    fun `does not accept uploads without a connected account`() {
        val gateway = object : TelegramGateway by FakeTelegramGateway() {
            override fun status(): TelegramStatus = TelegramStatus(state = "phone")
        }
        val component = UploadComponent(UploadStore(directory, Clock.systemUTC()), gateway)

        assertFailsWith<TelegramNotConnectedException> { component.create("photo.png", "image/png", byteArrayOf(1), 1) }
    }
}
