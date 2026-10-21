package ru.aigram.ai.research

import java.util.Locale

/** Unicode words for ranking and routing, never for rejecting a model's reply. */
class ResearchWords {
    fun tokens(value: String): List<String> {
        val normalized = StringBuilder(value.length)
        value.lowercase(Locale.ROOT).codePoints().forEach { codePoint ->
            if (Character.isLetterOrDigit(codePoint) ||
                Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()
            ) {
                normalized.appendCodePoint(codePoint)
            } else {
                normalized.append(' ')
            }
        }
        return normalized.toString().split(' ').filter(String::isNotEmpty)
    }
}
