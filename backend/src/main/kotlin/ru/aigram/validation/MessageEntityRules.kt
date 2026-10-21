package ru.aigram.validation

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import java.util.stream.IntStream

internal fun validMessageUnicode(text: String): Boolean = IntStream.range(0, text.length).noneMatch { index ->
    val current = text[index]
    Character.isHighSurrogate(current) &&
        (index + 1 >= text.length || !Character.isLowSurrogate(text[index + 1])) ||
        Character.isLowSurrogate(current) && (index == 0 || !Character.isHighSurrogate(text[index - 1]))
}

internal fun messageEntityBoundary(text: String, index: Int): Boolean = index == 0 || index == text.length ||
    !Character.isHighSurrogate(text[index - 1]) || !Character.isLowSurrogate(text[index])

internal fun validMessageLink(value: String): Boolean {
    if (value.isBlank() || value.codePoints().anyMatch { Character.isISOControl(it) || Character.isWhitespace(it) }) {
        return false
    }
    return try {
        val uri = URI(value)
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            "http", "https" -> !uri.host.isNullOrBlank() && uri.rawUserInfo == null
            "mailto" -> uri.isOpaque && !uri.rawSchemeSpecificPart.isNullOrBlank()
            "tg" -> !uri.host.isNullOrBlank() && uri.rawUserInfo == null
            else -> false
        }
    } catch (_: URISyntaxException) {
        false
    }
}
