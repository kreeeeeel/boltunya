package ru.aigram.automation

import kotlinx.serialization.json.Json
import ru.aigram.resources.ResourceCatalog
import java.util.Locale
import java.util.stream.Collectors

class AssistantTaskFilter {
    private val resources = ResourceCatalog()
    private val vocabulary = Json.decodeFromString<List<String>>(resources.text("languages.json")).stream()
        .map { language ->
            Json.decodeFromString<Map<String, List<String>>>(resources.text("automation/tasks/$language.json"))
        }
        .flatMap { entries ->
            entries.entries.stream().flatMap { (key, values) ->
                values.stream().map {
                    key to
                        tokens(it)
                }
            }
        }
        .collect(Collectors.groupingBy({ it.first }, Collectors.mapping({ it.second }, Collectors.toList())))

    fun ignores(text: String): Boolean {
        val words = tokens(text)
        return contains("instructions", words) && (
            contains("tasks", words) ||
                vocabulary["taskPrefixes"].orEmpty().stream().anyMatch { prefix ->
                    prefix.size == 1 && words.stream().anyMatch { it.startsWith(prefix[0]) }
                }
            )
    }

    private fun contains(key: String, words: List<String>): Boolean =
        vocabulary[key].orEmpty().stream().anyMatch { phrase ->
            phrase.isNotEmpty() && words.windowed(phrase.size).stream().anyMatch { it == phrase }
        }

    private fun tokens(text: String): List<String> = text.lowercase(Locale.ROOT).codePoints()
        .mapToObj {
            if (Character.isLetterOrDigit(it)) {
                String(Character.toChars(it))
            } else {
                " "
            }
        }
        .collect(Collectors.joining()).split(' ').filter(String::isNotEmpty)
}
