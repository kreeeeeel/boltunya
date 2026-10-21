package ru.aigram.ai.research

import ru.aigram.domain.ai.research.ResearchSource
import ru.aigram.exception.IntegrationException
import java.time.Clock

private const val RETRY_SECONDS = 300L

class FallbackSearchProvider(
    private val primary: SearchProvider,
    private val fallback: SearchProvider,
    private val selector: SearchResultSelector,
    private val clock: Clock,
) : SearchProvider {
    @Volatile private var blockedUntil = 0L

    @Volatile private var keyRevision = -1L

    override fun revision(): Long = primary.revision() + fallback.revision()

    @Synchronized
    override fun search(query: String): List<ResearchSource> {
        if (primary.revision() != keyRevision) {
            keyRevision = primary.revision()
            blockedUntil = 0
        }
        val found = if (clock.instant().epochSecond >= blockedUntil) {
            try {
                selector.select(query, primary.search(query))
            } catch (exception: IntegrationException) {
                System.err.println("Web search fallback: ${exception.javaClass.simpleName}")
                blockedUntil = clock.instant().epochSecond + RETRY_SECONDS
                emptyList()
            }
        } else {
            emptyList()
        }
        return found.ifEmpty { selector.select(query, fallback.search(query)) }
    }
}
