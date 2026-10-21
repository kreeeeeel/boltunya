package ru.aigram.ai.research

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.impl.InvalidResearchUrlException
import ru.aigram.exception.impl.MalformedResearchUrlException
import ru.aigram.exception.impl.PrivateResearchAddressException
import ru.aigram.exception.impl.ResearchPageUnavailableException
import ru.aigram.exception.impl.ResearchRedirectLimitException
import ru.aigram.exception.impl.ResearchRedirectMissingException
import java.net.InetAddress
import java.net.URI
import java.util.Arrays
import java.util.stream.Collectors

class PublicPageReader(
    private val allowedPorts: Set<Int> = setOf(-1, 80, 443),
    private val resolve: (String) -> Array<InetAddress> = InetAddress::getAllByName,
) {
    fun validate(url: String): URI {
        val uri = try {
            URI.create(url)
        } catch (exception: IllegalArgumentException) {
            throw MalformedResearchUrlException(exception)
        }
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() ||
            uri.userInfo != null || uri.port !in allowedPorts
        ) {
            throw InvalidResearchUrlException()
        }
        val addresses = resolve(uri.host)
        if (addresses.isEmpty() || Arrays.stream(addresses).anyMatch {
                it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress ||
                    it.isSiteLocalAddress || it.isMulticastAddress ||
                    (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc)
            }
        ) {
            throw PrivateResearchAddressException()
        }
        return uri
    }

    fun read(url: String): String = read(url, 0)

    private fun read(url: String, redirects: Int): String {
        if (redirects > 3) {
            throw ResearchRedirectLimitException()
        }
        val uri = validate(url)
        val response = Jsoup.connect(uri.toString()).userAgent("Boltun/1.1")
            .timeout(5000).maxBodySize(192 * 1024).followRedirects(false).ignoreHttpErrors(true).execute()
        if (response.statusCode() in setOf(301, 302, 303, 307, 308)) {
            val target = response.header("Location")
            if (target.isNullOrBlank()) {
                throw ResearchRedirectMissingException()
            }
            return read(uri.resolve(target).toString(), redirects + 1)
        }
        if (response.statusCode() != 200 || response.contentType()?.contains("html") != true) {
            throw ResearchPageUnavailableException()
        }
        val document = response.parse()
        val embedded = document.select("[data-content]").stream()
            .map { embeddedText(it.attr("data-content")) }.filter(String::isNotBlank)
            .collect(Collectors.joining("\n")).take(2000)
        val description = document.selectFirst("meta[name=description]")?.attr("content").orEmpty()
        document.select("script, style, nav, header, footer, form, aside").remove()
        val content = document.selectFirst("article, main") ?: document.body()
        return listOf(embedded, description, content.text()).stream().filter(String::isNotBlank)
            .collect(Collectors.joining("\n")).take(3500)
    }

    private fun embeddedText(encoded: String): String {
        if (!encoded.startsWith('{') || encoded.length > 64000) {
            return ""
        }
        return try {
            val data = Json.parseToJsonElement(encoded) as? JsonObject
            val paragraphs = data?.get("microFormat") as? JsonArray
            paragraphs?.stream()?.limit(8)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
                ?.map { Jsoup.parse(it).text() }?.collect(Collectors.joining("\n")).orEmpty()
        } catch (exception: SerializationException) {
            System.err.println("Embedded article data invalid: ${exception.javaClass.simpleName}")
            ""
        }
    }
}
