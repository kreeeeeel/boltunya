package ru.boltunya.resources

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/** UTF-8 resources are versioned with the backend; user data never becomes executable code. */
class ResourceCatalog @Inject constructor() {
    private val texts = ConcurrentHashMap<String, String>()
    private val patterns = ConcurrentHashMap<String, Regex>()

    fun text(path: String): String = texts.computeIfAbsent(path) {
        val input = javaClass.getResourceAsStream("/$path")
            ?: throw IllegalStateException("Missing application resource: $path")
        input.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText().trim() }
    }

    fun prompt(name: String, values: Map<String, Any?> = emptyMap()): String = render("prompts/$name.txt", values)

    fun render(path: String, values: Map<String, Any?> = emptyMap()): String =
        regex("common/resource-template").replace(text(path)) {
            val key = it.groupValues[1]
            if (values.containsKey(key)) values[key]?.toString().orEmpty() else it.value
        }

    fun regex(name: String): Regex = patterns.computeIfAbsent(name) {
        if (name.startsWith("intent/")) {
            val languages = Json.decodeFromString<List<String>>(text("languages.json"))
            Regex(
                "(?iuU)" + languages.stream().map { language ->
                    "(?:" + text("patterns/$name/$language.regex") + ")"
                }.toList().joinToString("|"),
            )
        } else {
            Regex(text("patterns/$name.regex"))
        }
    }
}
