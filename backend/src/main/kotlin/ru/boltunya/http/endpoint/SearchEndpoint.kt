package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.JsonObject
import ru.boltunya.ai.research.ExaMcpSearchClient
import ru.boltunya.ai.research.SearchConnection
import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.impl.MalformedPayloadException
import ru.boltunya.http.RequestReader
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.SearchCredentialStore

private const val MAX_KEY_CHARACTERS = 256

class SearchEndpoint @Inject constructor(
    private val credentials: SearchCredentialStore,
    private val search: ExaMcpSearchClient,
    private val input: RequestReader,
    private val resources: ResourceCatalog,
) {
    fun connection(): SearchConnection = search.connection()

    fun save(body: JsonObject): SearchConnection {
        if (body.keys != setOf("apiKey")) throw MalformedPayloadException()
        credentials.save(input.text(body, "apiKey", MAX_KEY_CHARACTERS))
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
            System.err.println("Search connection check failed: ${exception.javaClass.simpleName}")
        }
        return connection()
    }
}
