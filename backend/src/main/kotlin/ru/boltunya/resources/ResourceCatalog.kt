package ru.boltunya.resources

import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

/** UTF-8 resources are versioned with the backend; user data never becomes executable code. */
class ResourceCatalog @Inject constructor() {
    private val texts = ConcurrentHashMap<String, String>()

    fun text(path: String): String = texts.computeIfAbsent(path) {
        val input = javaClass.getResourceAsStream("/$path")
            ?: throw IllegalStateException("Missing application resource: $path")
        input.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText().trim() }
    }

    fun prompt(name: String, values: Map<String, Any?> = emptyMap()): String = render("prompts/$name.txt", values)

    fun render(path: String, values: Map<String, Any?> = emptyMap()): String = interpolate(text(path), values)

    fun interpolate(source: String, values: Map<String, Any?>, preserveUnknown: Boolean = true): String {
        val result = StringBuilder()
        var cursor = 0
        Stream.iterate(source.indexOf("{{")) { source.indexOf("{{", it + 2) }.takeWhile { it >= 0 }
            .forEach { start ->
                val end = source.indexOf("}}", start + 2)
                if (start >= cursor && end >= 0) {
                    val key = source.substring(start + 2, end)
                    if (key.isNotEmpty() && key.chars().allMatch {
                            it in 'a'.code..'z'.code || it in 'A'.code..'Z'.code || it in '0'.code..'9'.code ||
                                it == '_'.code || it == '.'.code || it == '-'.code
                        }
                    ) {
                        result.append(source, cursor, start)
                        if (values.containsKey(key)) {
                            result.append(values[key]?.toString().orEmpty())
                        } else if (preserveUnknown) {
                            result.append(source, start, end + 2)
                        }
                        cursor = end + 2
                    }
                }
            }
        return result.append(source, cursor, source.length).toString()
    }
}
