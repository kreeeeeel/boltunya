package ru.aigram.domain.memory

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

enum class MemoryRefreshField(val jsonName: String) {
    REBUILD("rebuild"),
    IMMEDIATE("immediate"),
    ;

    fun accepts(value: JsonElement): Boolean = value is JsonPrimitive && !value.isString && value.booleanOrNull != null
}
