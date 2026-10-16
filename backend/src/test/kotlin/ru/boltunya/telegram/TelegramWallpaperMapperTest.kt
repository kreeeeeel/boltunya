package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TelegramWallpaperMapperTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `maps Telegram photo wallpaper file and dimming without downloading history`() {
        val files = TelegramMediaStore(directory) { error("Should not download during mapping") }
        val wallpaper = TdApi.ChatBackground().apply {
            darkThemeDimming = 30
            background = TdApi.Background().apply {
                type = TdApi.BackgroundTypeWallpaper(true, false)
                document =
                    TdApi.Document().apply {
                        document = TdApi.File().apply { id = 76 }
                        mimeType = "image/jpeg"
                        fileName =
                            "wall.jpg"
                    }
            }
        }
        val result = assertNotNull(TelegramWallpaperMapper(files).map(wallpaper))
        assertEquals("/api/media/76", result.url)
        assertEquals("wallpaper", result.kind)
        assertEquals(30, result.dimming)
        assertEquals(true, result.blurred)
    }

    @Test
    fun `maps gradient colors and orientation`() {
        val files = TelegramMediaStore(directory) { error("Should not download") }
        val wallpaper = TdApi.ChatBackground().apply {
            background =
                TdApi.Background().apply {
                    type =
                        TdApi.BackgroundTypeFill(TdApi.BackgroundFillGradient(0xabcdef, 0x123456, 45))
                }
        }
        val result = assertNotNull(TelegramWallpaperMapper(files).map(wallpaper))
        assertEquals(listOf("#abcdef", "#123456"), result.colors)
        assertEquals(45, result.rotation)
    }
}
