package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import ru.boltunya.domain.ChatWallpaper
import java.util.Arrays

private const val RGB_MASK = 0xffffff

class TelegramWallpaperMapper @Inject constructor(private val files: TelegramMediaStore) {
    fun map(chat: TdApi.ChatBackground?): ChatWallpaper? {
        val background = chat?.background ?: return null
        val type = background.type
        val fill = when (type) {
            is TdApi.BackgroundTypeFill -> type.fill
            is TdApi.BackgroundTypePattern -> type.fill
            else -> null
        }
        val colors = when (fill) {
            is TdApi.BackgroundFillSolid -> listOf(color(fill.color))
            is TdApi.BackgroundFillGradient -> listOf(color(fill.topColor), color(fill.bottomColor))
            is TdApi.BackgroundFillFreeformGradient -> Arrays.stream(fill.colors).mapToObj(::color).toList()
            else -> emptyList()
        }
        val document = background.document
        val url = document?.let { "/api/media/${files.register(it.document, it.mimeType, it.fileName)}" }.orEmpty()
        return ChatWallpaper(
            kind = when (type) {
                is TdApi.BackgroundTypeWallpaper -> "wallpaper"
                is TdApi.BackgroundTypePattern -> "pattern"
                else -> "fill"
            },
            url = url,
            colors = colors,
            rotation = (fill as? TdApi.BackgroundFillGradient)?.rotationAngle ?: 0,
            blurred = (type as? TdApi.BackgroundTypeWallpaper)?.isBlurred ?: false,
            dimming = chat.darkThemeDimming,
            intensity = (type as? TdApi.BackgroundTypePattern)?.intensity ?: 50,
        )
    }

    private fun color(value: Int): String = "#%06x".format(value and RGB_MASK)
}
