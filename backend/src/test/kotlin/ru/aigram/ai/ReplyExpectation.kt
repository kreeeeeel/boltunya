package ru.aigram.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** Golden fixture assertions are test-only; they never reject a user's generated reply. */
class ReplyExpectation {
    fun checks(expected: JsonObject, reply: String): List<Boolean> {
        val text = reply.lowercase(Locale.ROOT)
        val words = text.codePoints().collect(
            { StringBuilder() },
            { target, point ->
                target.appendCodePoint(
                    if (Character.isLetterOrDigit(point)) {
                        point
                    } else {
                        ' '.code
                    },
                )
            },
            { first, second -> first.append(second) },
        ).toString().split(' ').filter(String::isNotEmpty)
        val checks = mutableListOf<Boolean>()
        expected["any"]?.jsonArray?.stream()?.forEach { alternatives ->
            checks.add(alternatives.jsonArray.any { text.contains(it.jsonPrimitive.content.lowercase(Locale.ROOT)) })
        }
        expected["absent"]?.jsonArray?.stream()?.forEach { forbidden ->
            checks.add(!text.contains(forbidden.jsonPrimitive.content.lowercase(Locale.ROOT)))
        }
        expected["wordPrefixes"]?.jsonArray?.let { prefixes ->
            checks.add(words.any { word -> prefixes.any { word.startsWith(it.jsonPrimitive.content) } })
        }
        if (expected["uppercase"]?.jsonPrimitive?.booleanOrNull == true) {
            checks.add(reply.firstOrNull()?.isUpperCase() == true)
        }
        if (expected["noOwnerAction"]?.jsonPrimitive?.booleanOrNull == true) {
            val claims = words.any { it in setOf("откладываю", "отправляю", "отсылаю") } ||
                words.withIndex().any { (index, word) ->
                    word in setOf("я", "уже") && words.drop(index + 1).take(8).any {
                        it in setOf("отправил", "успел", "высылал")
                    }
                } || text.contains("файл отправлен") || text.contains("файл уже отправлен")
            checks.add(!claims)
        }
        return checks
    }
}
