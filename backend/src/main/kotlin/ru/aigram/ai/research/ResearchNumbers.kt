package ru.aigram.ai.research

import java.math.BigDecimal
import java.util.Collections
import java.util.StringTokenizer
import java.util.stream.Stream

private val NUMBER_SPACES = setOf(' ', '\u00a0', '\u202f')

/** Decimal lexer accepts grouping spaces but never interprets a dotted date as a decimal. */
class ResearchNumbers {
    fun values(text: String): Set<BigDecimal> {
        val tokens = StringBuilder(text.length)
        text.chars().forEach { code ->
            val character = code.toChar()
            tokens.append(
                if (character.isDigit() || character in NUMBER_SPACES || character == '.' ||
                    character == ','
                ) {
                    character
                } else {
                    '|'
                },
            )
        }
        return Collections.list(StringTokenizer(tokens.toString(), "|")).stream()
            .map { it.toString().trim() }.flatMap { chunk ->
                val parts = chunk.split(' ', '\u00a0', '\u202f').filter(String::isNotBlank)
                val grouped = parts.size > 1 && parts.first().length in 1..3 &&
                    parts.drop(1).all { it.substringBefore('.').substringBefore(',').length == 3 }
                (if (grouped) listOf(parts.joinToString("")) else parts).stream()
            }.map { candidate ->
                val value = candidate.trim('.', ',')
                if (value.count { it == '.' || it == ',' } > 1) null else value.replace(',', '.').toBigDecimalOrNull()
            }.flatMap { value ->
                if (value == null) Stream.empty() else Stream.of(value.stripTrailingZeros())
            }.toList().toSet()
    }
}
