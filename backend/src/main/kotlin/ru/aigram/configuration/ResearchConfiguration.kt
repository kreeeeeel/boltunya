package ru.aigram.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import ru.aigram.ai.MessageContextFormatter
import ru.aigram.ai.research.CurrencyRatesClient
import ru.aigram.ai.research.ExaMcpSearchClient
import ru.aigram.ai.research.ExaResultParser
import ru.aigram.ai.research.FallbackSearchProvider
import ru.aigram.ai.research.InternetResearcher
import ru.aigram.ai.research.NewsSearchClient
import ru.aigram.ai.research.OptionalExaSearchProvider
import ru.aigram.ai.research.PublicPageReader
import ru.aigram.ai.research.ResearchLinkPolicy
import ru.aigram.ai.research.ResearchPlanner
import ru.aigram.ai.research.ResearchUrl
import ru.aigram.ai.research.SearchProvider
import ru.aigram.ai.research.SearchResultSelector
import ru.aigram.ai.research.WeatherClient
import ru.aigram.ai.research.WebSearchClient
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SearchCredentialStore
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class ResearchConfiguration {
    @Bean
    fun weatherClient(): WeatherClient = WeatherClient()

    @Bean
    fun publicPageReader(): PublicPageReader = PublicPageReader()

    @Bean
    fun researchUrl(): ResearchUrl = ResearchUrl()

    @Bean
    fun searchResultSelector(resources: ResourceCatalog, urls: ResearchUrl, clock: Clock): SearchResultSelector =
        SearchResultSelector(resources, urls, clock)

    @Bean
    fun currencyRatesClient(clock: Clock, resources: ResourceCatalog): CurrencyRatesClient =
        CurrencyRatesClient(clock, resources = resources)

    @Bean
    fun newsSearchClient(clock: Clock, resources: ResourceCatalog, urls: ResearchUrl): NewsSearchClient =
        NewsSearchClient(clock, resources, urls)

    @Bean
    fun researchLinkPolicy(resources: ResourceCatalog, urls: ResearchUrl): ResearchLinkPolicy =
        ResearchLinkPolicy(resources, urls)

    @Bean
    fun exaMcpSearchClient(credentials: SearchCredentialStore, parser: ExaResultParser): ExaMcpSearchClient =
        ExaMcpSearchClient(credentials, parser)

    @Bean
    fun webSearchClient(
        pages: PublicPageReader,
        selector: SearchResultSelector,
        news: NewsSearchClient,
    ): WebSearchClient = WebSearchClient(pages = pages, selector = selector, news = news)

    @Bean
    fun optionalExaSearchProvider(client: ExaMcpSearchClient): OptionalExaSearchProvider =
        OptionalExaSearchProvider(client)

    @Bean
    @Primary
    fun searchProvider(
        primary: WebSearchClient,
        fallback: OptionalExaSearchProvider,
        selector: SearchResultSelector,
        clock: Clock,
    ): FallbackSearchProvider = FallbackSearchProvider(primary, fallback, selector, clock)

    @Bean
    fun internetResearcher(
        weather: WeatherClient,
        search: SearchProvider,
        clock: Clock,
        selector: SearchResultSelector,
        pages: PublicPageReader,
        currencies: CurrencyRatesClient,
    ): InternetResearcher = InternetResearcher(weather, search, clock, selector, pages::read, currencies)

    @Bean
    fun researchPlanner(clock: Clock, authors: MessageContextFormatter): ResearchPlanner =
        ResearchPlanner(clock, authors)
}
