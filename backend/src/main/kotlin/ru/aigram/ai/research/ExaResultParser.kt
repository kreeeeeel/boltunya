package ru.aigram.ai.research

import org.springframework.stereotype.Component
import ru.aigram.domain.ai.research.ResearchSource
import ru.aigram.exception.IntegrationException
import ru.aigram.resources.ResourceCatalog
import java.io.IOException

private const val MAX_RESULTS = 6
private const val MAX_RESPONSE_CHARACTERS = 200_000
private const val MAX_SOURCE_CHARACTERS = 4000
private const val MAX_TITLE_CHARACTERS = 180

@Component
class ExaResultParser(private val resources: ResourceCatalog, private val pages: PublicPageReader) {
    fun parse(text: String): List<ResearchSource> = text.take(MAX_RESPONSE_CHARACTERS).lines()
        .fold(mutableListOf<MutableList<String>>()) { records, line ->
            if (line.startsWith("Title:")) {
                records.add(mutableListOf())
            }
            records.lastOrNull()?.add(line)
            records
        }.take(MAX_RESULTS).mapNotNull { lines ->
            val title = lines.first().substringAfter(':').trim()
            val url = lines.firstOrNull { it.startsWith("URL:") }?.substringAfter(':')?.trim().orEmpty()
            val published = lines.firstOrNull { it.startsWith("Published:") || it.startsWith("PublishedDate:") }
                ?.substringAfter(':')?.trim().orEmpty()
            val start = lines.indexOfFirst { it.startsWith("Text:") || it.startsWith("Highlights:") }
            if (url.isBlank() || title.isBlank() || start < 0) {
                return@mapNotNull null
            }
            val content = (listOf(lines[start].substringAfter(':')) + lines.drop(start + 1))
                .dropLastWhile { it.isBlank() || it.trim() == "---" }.joinToString("\n").trim()
            try {
                pages.validate(url)
                ResearchSource(
                    title.take(MAX_TITLE_CHARACTERS),
                    url,
                    content.take(MAX_SOURCE_CHARACTERS),
                    published.takeUnless { it == "N/A" }.orEmpty(),
                )
            } catch (exception: IntegrationException) {
                System.err.println("MCP search link rejected: ${exception.javaClass.simpleName}")
                null
            } catch (exception: IOException) {
                System.err.println("MCP search link unavailable: ${exception.javaClass.simpleName}")
                null
            }
        }.toList()
}
