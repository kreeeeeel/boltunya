package ru.aigram.ai.research

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.impl.WebSearchUnavailableException
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.stream.Stream

class WebSearchClient(
    private val duckEndpoint: String = "https://html.duckduckgo.com/html/",
    private val bingEndpoint: String = "https://www.bing.com/search",
    private val pages: PublicPageReader = PublicPageReader(),
    private val selector: SearchResultSelector = SearchResultSelector(),
    private val news: NewsSearchClient = NewsSearchClient(),
) : SearchProvider {
    override fun search(query: String): List<ResearchSource> {
        val headlines = news.search(query)
        if (headlines != null) {
            return headlines.mapNotNull { item ->
                source(item.title, item.url, item.text)?.copy(publishedAt = item.publishedAt)
            }
        }
        val primary = try {
            duck(query)
        } catch (exception: IOException) {
            System.err.println("Search provider unavailable: ${exception.javaClass.simpleName}")
            emptyList()
        }
        val relevant = selector.select(query, primary)
        val results = relevant.ifEmpty { selector.select(query, bing(query)) }
        return results.distinctBy { it.url }
    }

    private fun duck(query: String): List<ResearchSource> {
        val document = Jsoup.connect(duckEndpoint).userAgent("Boltun/1.1")
            .data("q", query).timeout(7000).maxBodySize(512 * 1024).get()
        return document.select(".result").stream().map { result ->
            val link = result.selectFirst(".result__a")
            val url = link?.attr("href").orEmpty()
            val absolute = if (url.startsWith("//")) "https:$url" else url
            val uri = runCatching { URI.create(absolute) }.getOrNull()
            val redirect = uri?.rawQuery?.split('&')?.firstOrNull { it.startsWith("uddg=") }
            val target = if (redirect != null) {
                URLDecoder.decode(redirect.substringAfter('='), StandardCharsets.UTF_8)
            } else {
                absolute
            }
            source(link?.text().orEmpty(), target, result.selectFirst(".result__snippet")?.text().orEmpty())
        }.flatMap<ResearchSource> { if (it == null) Stream.empty() else Stream.of(it) }.limit(5).toList()
    }

    private fun bing(query: String): List<ResearchSource> {
        val response = try {
            Jsoup.connect(bingEndpoint).userAgent("Boltun/1.1").data("q", query).data("format", "rss")
                .timeout(7000).maxBodySize(512 * 1024).ignoreContentType(true).execute()
        } catch (exception: IOException) {
            throw WebSearchUnavailableException(exception)
        }
        val document = Jsoup.parse(response.body(), "", Parser.xmlParser())
        return document.select("channel > item").stream().map { item ->
            source(
                item.selectFirst("title")?.text().orEmpty(),
                item.selectFirst("link")?.text().orEmpty(),
                item.selectFirst("description")?.text().orEmpty(),
            )
        }.flatMap<ResearchSource> { if (it == null) Stream.empty() else Stream.of(it) }.limit(5).toList()
    }

    private fun source(title: String, url: String, text: String): ResearchSource? {
        if (title.isBlank() || url.length > 2048) return null
        return try {
            pages.validate(url)
            ResearchSource(title.take(180), url, Jsoup.parse(text).text().take(650))
        } catch (exception: IntegrationException) {
            System.err.println("Search link rejected: ${exception.javaClass.simpleName}")
            null
        } catch (exception: IOException) {
            System.err.println("Search link unavailable: ${exception.javaClass.simpleName}")
            null
        }
    }
}
