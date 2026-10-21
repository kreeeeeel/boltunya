package ru.aigram.client.configuration

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import ru.aigram.client.AigramClient
import ru.aigram.client.exception.impl.InvalidClientSessionException
import ru.aigram.client.exception.impl.InvalidClientUriException
import ru.aigram.client.generated.infrastructure.Serializer
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration

private const val SESSION_COOKIE = "tiho"
private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
private val CONNECT_TIMEOUT = Duration.ofSeconds(10)

class AigramClientFactory {
    /** The caller supplies the API origin and keeps ownership of session acquisition and rotation. */
    fun create(baseUri: URI, sessionToken: () -> String): AigramClient {
        if (baseUri.scheme.orEmpty().lowercase() !in setOf("http", "https") || baseUri.host.isNullOrBlank() ||
            baseUri.rawUserInfo != null || baseUri.rawQuery != null || baseUri.rawFragment != null ||
            baseUri.rawPath.orEmpty() !in setOf("", "/") || baseUri.port == 0 || baseUri.port > 65535
        ) {
            throw InvalidClientUriException()
        }
        val transport = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
        try {
            val client = WebClient.builder()
                .baseUrl(baseUri.toASCIIString().trimEnd('/'))
                .clientConnector(JdkClientHttpConnector(transport))
                .codecs {
                    it.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES)
                    it.defaultCodecs().jackson2JsonEncoder(
                        Jackson2JsonEncoder(Serializer.jacksonObjectMapper, MediaType.APPLICATION_JSON),
                    )
                    it.defaultCodecs().jackson2JsonDecoder(
                        Jackson2JsonDecoder(Serializer.jacksonObjectMapper, MediaType.APPLICATION_JSON),
                    )
                }
                .filter(session(sessionToken))
                .build()
            return AigramClient(client, transport)
        } catch (exception: Exception) {
            transport.close()
            throw exception
        }
    }

    private fun session(source: () -> String): ExchangeFilterFunction = ExchangeFilterFunction { request, next ->
        Mono.defer {
            val token = source()
            if (token.isEmpty() || !token.chars().allMatch {
                    it in 0x21..0x7e && it != '"'.code && it != ','.code && it != ';'.code && it != '\\'.code
                }
            ) {
                throw InvalidClientSessionException()
            }
            val authorized = ClientRequest.from(request)
                .headers { it.remove(HttpHeaders.COOKIE) }
                .cookies { it.set(SESSION_COOKIE, token) }
                .build()
            next.exchange(authorized)
        }
    }
}
