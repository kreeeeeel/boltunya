package ru.aigram.contract.validation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.util.HexFormat
import java.util.stream.Collectors

class WireFingerprint {
    fun digest(value: JsonElement): JsonPrimitive {
        val bytes = canonical(value).toByteArray(Charsets.UTF_8)
        return JsonPrimitive(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
    }

    private fun canonical(value: JsonElement): String = when (value) {
        is JsonObject -> value.entries.stream().sorted(compareBy { it.key })
            .map { JsonPrimitive(it.key).toString() + ":" + canonical(it.value) }
            .collect(Collectors.joining(",", "{", "}"))
        is JsonArray -> value.stream().map(::canonical).collect(Collectors.joining(",", "[", "]"))
        is JsonPrimitive -> {
            if (value.isString || value == JsonNull || value.content in setOf("true", "false")) {
                value.toString()
            } else {
                value.content.toBigDecimal().stripTrailingZeros().toPlainString()
            }
        }
    }
}
