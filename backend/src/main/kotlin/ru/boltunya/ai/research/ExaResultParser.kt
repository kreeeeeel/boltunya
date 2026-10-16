package ru.boltunya.ai.research

import jakarta.inject.Inject
import ru.boltunya.exception.IntegrationException
import ru.boltunya.resources.ResourceCatalog
import java.io.IOException

private const val MAX_RESULTS = 6
private const val MAX_RESPONSE_CHARACTERS = 200_000
private const val MAX_SOURCE_CHARACTERS = 4000
private const val MAX_TITLE_CHARACTERS = 180

class ExaResultParser @Inject constructor(
    private val resources: ResourceCatalog,
    private val pages: PublicPageReader,
) {
    fun parse(text: String): List<ResearchSource> = resources.regex("common/exa-result")
        .findAll(text.take(MAX_RESPONSE_CHARACTERS)).take(MAX_RESULTS).mapNotNull { match ->
            val url = match.groupValues[2]
            try {
                pages.validate(url)
                ResearchSource(
                    match.groupValues[1].take(MAX_TITLE_CHARACTERS),
                    url,
                    match.groupValues[4].trim().take(MAX_SOURCE_CHARACTERS),
                    match.groupValues[3].takeIf { it != "N/A" }.orEmpty(),
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
