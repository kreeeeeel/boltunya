package ru.aigram.component.settings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import ru.aigram.ai.research.ExaMcpSearchClient
import ru.aigram.domain.ai.research.SearchConnection
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.impl.MalformedPayloadException
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SearchCredentialStore

private const val MAX_KEY_CHARACTERS = 256
private val LOGGER = LoggerFactory.getLogger(SearchComponent::class.java)

@Component
class SearchComponent(
    private val credentials: SearchCredentialStore,
    private val search: ExaMcpSearchClient,
    private val resources: ResourceCatalog,
) {
    fun connection(): SearchConnection = search.connection()

    fun save(apiKey: String): SearchConnection {
        if (apiKey.isBlank() || apiKey.length > MAX_KEY_CHARACTERS) {
            throw MalformedPayloadException()
        }
        credentials.save(apiKey)
        search.reset()
        return connection()
    }

    fun delete(): SearchConnection {
        credentials.delete()
        search.reset()
        return connection()
    }

    fun check(): SearchConnection {
        try {
            search.search(resources.prompt("ru/research/connection-query"))
        } catch (exception: IntegrationException) {
            LOGGER.warn("Search connection check failed: {}", exception.javaClass.simpleName)
        }
        return connection()
    }
}
