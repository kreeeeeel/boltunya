package ru.aigram.ai

internal fun dominantWritingSystem(text: String): Character.UnicodeScript = text.filter(Char::isLetter)
    .groupingBy { Character.UnicodeScript.of(it.code) }
    .eachCount().maxByOrNull { it.value }?.key ?: Character.UnicodeScript.COMMON
