package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.exception.impl.UnknownMediaException
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramStickerRegistryTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `maps trusted Telegram sticker metadata and resolves only known file IDs`() {
        val files = TelegramMediaStore(directory) { error("No downloads in registry tests") }
        val registry = TelegramStickerRegistry(files)
        registry.activate("42")
        val item = assertNotNull(registry.remember(sticker(11)))
        assertEquals("11", item.id)
        assertEquals("application/x-tgsticker", item.media.mimeType)
        assertEquals(256, item.media.width)
        assertTrue(registry.allowsSet(5))
        val input = registry.content(item.id)
        assertEquals(11, assertIs<TdApi.InputFileId>(input.sticker.sticker).id)
        assertEquals("🙂", input.emoji)
        assertFailsWith<UnknownMediaException> { registry.resolve("https://example.com/sticker") }
        assertFailsWith<UnknownMediaException> { registry.resolve("12") }
        registry.activate("43")
        assertFalse(registry.allowsSet(5))
        assertFailsWith<UnknownMediaException> { registry.resolve(item.id) }
    }

    @Test
    fun `rejects custom emoji types and replaced TDLib file identities`() {
        val files = TelegramMediaStore(directory) { error("No downloads in registry tests") }
        val registry = TelegramStickerRegistry(files)
        val invalid = sticker(1).apply { fullType = TdApi.StickerFullTypeMask() }
        assertNull(registry.remember(invalid))
        val item = assertNotNull(registry.remember(sticker(2)))
        val replacement = TdApi.File().apply {
            id = 2
            remote = TdApi.RemoteFile().apply { uniqueId = "different" }
        }
        files.register(replacement, "image/webp", "different.webp")
        assertFailsWith<UnknownMediaException> { registry.resolve(item.id) }
    }

    private fun sticker(id: Int) = TdApi.Sticker().apply {
        setId = 5
        width = 256
        height = 256
        emoji = "🙂"
        format = TdApi.StickerFormatTgs()
        fullType = TdApi.StickerFullTypeRegular()
        sticker = TdApi.File().apply {
            this.id = id
            remote = TdApi.RemoteFile().apply { uniqueId = "synthetic-$id" }
        }
    }
}
