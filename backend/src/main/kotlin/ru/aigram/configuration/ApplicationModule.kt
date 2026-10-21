package ru.aigram.configuration

import com.google.inject.AbstractModule
import com.google.inject.Provides
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.serialization.json.Json
import org.springframework.ai.chat.model.ChatModel
import ru.aigram.ai.ComputeDeviceDiscovery
import ru.aigram.ai.ConversationService
import ru.aigram.ai.DownloadWriter
import ru.aigram.ai.EngineManager
import ru.aigram.ai.EngineProcessLocator
import ru.aigram.ai.MessageContextFormatter
import ru.aigram.ai.ModelCatalog
import ru.aigram.ai.ModelInference
import ru.aigram.ai.NvidiaDeviceDiscovery
import ru.aigram.ai.OllamaDeviceSelector
import ru.aigram.ai.OllamaInstallation
import ru.aigram.ai.OllamaModelInventory
import ru.aigram.ai.OllamaRuntime
import ru.aigram.ai.PersonalStyleBuilder
import ru.aigram.ai.PromptBuilder
import ru.aigram.ai.RegistryBlobDownloader
import ru.aigram.ai.RegistryModelInstaller
import ru.aigram.ai.ReplyImageContext
import ru.aigram.ai.ReplyMediaPlanner
import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.ai.model.ModelPolicies
import ru.aigram.ai.model.ModelReplyReader
import ru.aigram.ai.model.ModelResponseStream
import ru.aigram.ai.model.SpringAiModelFactory
import ru.aigram.ai.profile.ModelCredentialVault
import ru.aigram.ai.profile.ModelProfileStore
import ru.aigram.ai.profile.ModelRoutingStore
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.ai.profile.ProfileChatOptions
import ru.aigram.ai.profile.ProfileContextBudget
import ru.aigram.ai.profile.ProfileModelFactory
import ru.aigram.ai.research.CurrencyRatesClient
import ru.aigram.ai.research.ExaMcpSearchClient
import ru.aigram.ai.research.ExaResultParser
import ru.aigram.ai.research.FallbackSearchProvider
import ru.aigram.ai.research.InternetResearcher
import ru.aigram.ai.research.NewsSearchClient
import ru.aigram.ai.research.OptionalExaSearchProvider
import ru.aigram.ai.research.PublicPageReader
import ru.aigram.ai.research.ResearchFactGuard
import ru.aigram.ai.research.ResearchLinkPolicy
import ru.aigram.ai.research.ResearchPlanner
import ru.aigram.ai.research.ResearchUrl
import ru.aigram.ai.research.SearchProvider
import ru.aigram.ai.research.SearchResultSelector
import ru.aigram.ai.research.WeatherClient
import ru.aigram.ai.research.WebSearchClient
import ru.aigram.application.ApplicationRuntime
import ru.aigram.application.ProcessExit
import ru.aigram.automation.MemoryWorkQueue
import ru.aigram.automation.PersonalReplyGenerator
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.ReplyDeliveryPolicy
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.http.ApiRouter
import ru.aigram.http.ExitAction
import ru.aigram.http.InputValidation
import ru.aigram.http.LocalSession
import ru.aigram.http.LocalWebServer
import ru.aigram.http.MediaResponder
import ru.aigram.http.RequestReader
import ru.aigram.http.ResourceResponder
import ru.aigram.http.ResponseWriter
import ru.aigram.http.endpoint.ActivityEndpoint
import ru.aigram.http.endpoint.AppearanceEndpoint
import ru.aigram.http.endpoint.AttachmentEndpoint
import ru.aigram.http.endpoint.AuthenticationEndpoint
import ru.aigram.http.endpoint.ChatEndpoint
import ru.aigram.http.endpoint.ContextExclusionEndpoint
import ru.aigram.http.endpoint.DiagnosticsEndpoint
import ru.aigram.http.endpoint.DraftEndpoint
import ru.aigram.http.endpoint.EngineEndpoint
import ru.aigram.http.endpoint.MediaEndpoint
import ru.aigram.http.endpoint.MemoryEndpoint
import ru.aigram.http.endpoint.ModelProfilesEndpoint
import ru.aigram.http.endpoint.PreviewEndpoint
import ru.aigram.http.endpoint.SearchEndpoint
import ru.aigram.http.endpoint.SettingsEndpoint
import ru.aigram.http.endpoint.StateEndpoint
import ru.aigram.http.endpoint.UploadEndpoint
import ru.aigram.media.UploadStore
import ru.aigram.memory.MemoryBatchBuilder
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.memory.MemoryProcessor
import ru.aigram.memory.MemoryStore
import ru.aigram.memory.MemorySummarizer
import ru.aigram.memory.OllamaMemorySummarizer
import ru.aigram.memory.OwnerMemorySummarizer
import ru.aigram.memory.ParticipantRosterStore
import ru.aigram.monitoring.MonitoringService
import ru.aigram.monitoring.NvidiaMonitor
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.ActivityFeedbackStore
import ru.aigram.storage.AppearanceStore
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.CredentialStore
import ru.aigram.storage.MessageArchiveStore
import ru.aigram.storage.ModelMeasurementStore
import ru.aigram.storage.SearchCredentialStore
import ru.aigram.storage.SettingsStore
import ru.aigram.storage.StorageLocations
import ru.aigram.storage.WritingStyleStore
import ru.aigram.telegram.MediaDownloadPolicy
import ru.aigram.telegram.MediaPrefetcher
import ru.aigram.telegram.StorageClient
import ru.aigram.telegram.TdLightGateway
import ru.aigram.telegram.TdLightStorageClient
import ru.aigram.telegram.TelegramCache
import ru.aigram.telegram.TelegramCacheStorage
import ru.aigram.telegram.TelegramChatDirectory
import ru.aigram.telegram.TelegramChatLoader
import ru.aigram.telegram.TelegramConnectionState
import ru.aigram.telegram.TelegramEntityMapper
import ru.aigram.telegram.TelegramGalleryService
import ru.aigram.telegram.TelegramGateway
import ru.aigram.telegram.TelegramHistoryReader
import ru.aigram.telegram.TelegramHistoryTracker
import ru.aigram.telegram.TelegramInteraction
import ru.aigram.telegram.TelegramMediaContent
import ru.aigram.telegram.TelegramMediaDeliveryTracker
import ru.aigram.telegram.TelegramMediaSender
import ru.aigram.telegram.TelegramMediaStore
import ru.aigram.telegram.TelegramMessageLinks
import ru.aigram.telegram.TelegramMessageMapper
import ru.aigram.telegram.TelegramMessageSender
import ru.aigram.telegram.TelegramMessageView
import ru.aigram.telegram.TelegramStickerRegistry
import ru.aigram.telegram.TelegramStickerService
import ru.aigram.telegram.TelegramTypingStore
import ru.aigram.telegram.TelegramUpdateHandler
import ru.aigram.telegram.TelegramWallpaperMapper
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.UUID
import java.util.stream.Stream

class ApplicationModule(private val configuration: ApplicationConfiguration) : AbstractModule() {
    override fun configure() {
        binder().requireExplicitBindings()
        bind(ApplicationConfiguration::class.java).toInstance(configuration)
        bind(ApplicationRuntime::class.java).`in`(Singleton::class.java)
        bind(TelegramGateway::class.java).to(TdLightGateway::class.java)
        bind(MemorySummarizer::class.java).to(OllamaMemorySummarizer::class.java)
        bind(StorageClient::class.java).to(TdLightStorageClient::class.java)
        bind(ComputeDeviceDiscovery::class.java).to(NvidiaDeviceDiscovery::class.java)
        bind(OwnerMemorySummarizer::class.java).to(OllamaMemorySummarizer::class.java)
        bind(ExitAction::class.java).to(ProcessExit::class.java).`in`(Singleton::class.java)
        bind(Path::class.java).toInstance(configuration.directory)
        Stream.of(
            PersonalReplyGenerator::class.java,
            ReplyDeliveryPolicy::class.java,
            ReplyImageContext::class.java,
            ReplyMediaPlanner::class.java,
            UploadStore::class.java,
            UploadEndpoint::class.java,
            AttachmentEndpoint::class.java,
            ContextExclusionEndpoint::class.java,
            ContextExclusionStore::class.java,
            TelegramMediaSender::class.java,
            TelegramMediaContent::class.java,
            TelegramMediaDeliveryTracker::class.java,
            TelegramTypingStore::class.java,
            TelegramGalleryService::class.java,
            TelegramMessageLinks::class.java,
            TelegramStickerService::class.java,
            TelegramStickerRegistry::class.java,
            TdLightGateway::class.java,
            ResourceCatalog::class.java,
            SearchCredentialStore::class.java,
            SearchEndpoint::class.java,
            ExaResultParser::class.java,
            ModelInference::class.java,
            ModelCredentialVault::class.java,
            ModelProfileStore::class.java,
            ModelRoutingStore::class.java,
            ModelTargetResolver::class.java,
            ProfileChatOptions::class.java,
            ProfileContextBudget::class.java,
            ProfileModelFactory::class.java,
            ModelProfilesEndpoint::class.java,
            AppearanceEndpoint::class.java,
            AppearanceStore::class.java,
            MemoryStore::class.java,
            ParticipantRosterStore::class.java,
            MemoryCoordinator::class.java,
            MemoryProcessor::class.java,
            MemoryBatchBuilder::class.java,
            OllamaMemorySummarizer::class.java,
            MemoryEndpoint::class.java,
            TelegramCache::class.java,
            TelegramCacheStorage::class.java,
            TdLightStorageClient::class.java,
            MediaDownloadPolicy::class.java,
            MediaPrefetcher::class.java,
            TelegramHistoryTracker::class.java,
            MessageArchiveStore::class.java,
            TelegramConnectionState::class.java,
            TelegramMessageView::class.java,
            TelegramChatDirectory::class.java,
            TelegramHistoryReader::class.java,
            TelegramChatLoader::class.java,
            TelegramUpdateHandler::class.java,
            TelegramMessageSender::class.java,
            TelegramMessageMapper::class.java,
            TelegramEntityMapper::class.java,
            TelegramWallpaperMapper::class.java,
            LocalWebServer::class.java,
            ApiRouter::class.java,
            InputValidation::class.java,
            MediaResponder::class.java,
            RequestReader::class.java,
            ResponseWriter::class.java,
            ResourceResponder::class.java,
            AuthenticationEndpoint::class.java,
            ChatEndpoint::class.java,
            EngineEndpoint::class.java,
            MediaEndpoint::class.java,
            PreviewEndpoint::class.java,
            DraftEndpoint::class.java,
            SettingsEndpoint::class.java,
            StateEndpoint::class.java,
            PersonalStyleBuilder::class.java,
            MessageContextFormatter::class.java,
            ResearchFactGuard::class.java,
            EngineManager::class.java,
            NvidiaDeviceDiscovery::class.java,
            OllamaDeviceSelector::class.java,
            EngineProcessLocator::class.java,
            DownloadWriter::class.java,
            RegistryBlobDownloader::class.java,
            ModelCatalog::class.java,
            MonitoringService::class.java,
            NvidiaMonitor::class.java,
            CredentialStore::class.java,
            AutomatedReplyStore::class.java,
            ActivityFeedbackStore::class.java,
            WritingStyleStore::class.java,
            ModelMeasurementStore::class.java,
            ConversationService::class.java,
            ChatCompletionService::class.java,
            ModelPolicies::class.java,
            ModelReplyReader::class.java,
            SpringAiModelFactory::class.java,
            ReplyTraceScope::class.java,
            ActivityEndpoint::class.java,
            DiagnosticsEndpoint::class.java,
            DiagnosticLog::class.java,
        ).forEach { bind(it).`in`(Singleton::class.java) }
    }

    @Provides @Singleton
    fun settings(): SettingsStore = SettingsStore(configuration.directory)

    @Provides @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides @Singleton
    fun registryHttp(): HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(15)).build()

    @Provides @Singleton
    fun installer(models: ModelCatalog, http: HttpClient, downloader: RegistryBlobDownloader): RegistryModelInstaller =
        RegistryModelInstaller(models = models, http = http, downloader = downloader)

    @Provides @Singleton
    fun weather(): WeatherClient = WeatherClient()

    @Provides @Singleton
    fun pages(): PublicPageReader = PublicPageReader()

    @Provides @Singleton
    fun researchUrl(): ResearchUrl = ResearchUrl()

    @Provides @Singleton
    fun searchSelector(resources: ResourceCatalog, urls: ResearchUrl, clock: Clock): SearchResultSelector =
        SearchResultSelector(resources, urls, clock)

    @Provides @Singleton
    fun currencies(clock: Clock, resources: ResourceCatalog): CurrencyRatesClient =
        CurrencyRatesClient(clock, resources = resources)

    @Provides @Singleton
    fun news(clock: Clock, resources: ResourceCatalog, urls: ResearchUrl): NewsSearchClient =
        NewsSearchClient(clock, resources, urls)

    @Provides @Singleton
    fun researchLinks(resources: ResourceCatalog, urls: ResearchUrl): ResearchLinkPolicy =
        ResearchLinkPolicy(resources, urls)

    @Provides @Singleton
    fun mcpSearch(credentials: SearchCredentialStore, parser: ExaResultParser): ExaMcpSearchClient =
        ExaMcpSearchClient(credentials, parser)

    @Provides @Singleton
    fun search(
        pages: PublicPageReader,
        mcp: ExaMcpSearchClient,
        selector: SearchResultSelector,
        clock: Clock,
        news: NewsSearchClient,
    ): SearchProvider = FallbackSearchProvider(
        WebSearchClient(pages = pages, selector = selector, news = news),
        OptionalExaSearchProvider(mcp),
        selector,
        clock,
    )

    @Provides @Singleton
    fun prompts(
        clock: Clock,
        personalStyle: PersonalStyleBuilder,
        links: ResearchLinkPolicy,
        authors: MessageContextFormatter,
    ): PromptBuilder = PromptBuilder(clock, personalStyle, links, authors)

    @Provides @Singleton
    fun researcher(
        weather: WeatherClient,
        search: SearchProvider,
        clock: Clock,
        selector: SearchResultSelector,
        pages: PublicPageReader,
        currencies: CurrencyRatesClient,
    ): InternetResearcher = InternetResearcher(weather, search, clock, selector, pages::read, currencies)

    @Provides @Singleton
    fun planner(clock: Clock, authors: MessageContextFormatter): ResearchPlanner = ResearchPlanner(clock, authors)

    @Provides @Singleton
    fun ollamaRuntime(): OllamaRuntime = OllamaRuntime(configuration.ollamaUrl)

    @Provides
    @Singleton
    fun ollamaInstallation(): OllamaInstallation = OllamaInstallation(configuration.directory)

    @Provides @Singleton
    fun storageLocations(installation: OllamaInstallation, resources: ResourceCatalog): StorageLocations =
        StorageLocations(configuration, installation, resources)

    @Provides
    @Singleton
    fun ollamaModelInventory(): OllamaModelInventory = OllamaModelInventory()

    @Provides @Singleton
    fun chatModel(factory: SpringAiModelFactory): ChatModel = factory.create(configuration.ollamaUrl)

    @Provides @Singleton
    fun modelResponseStream(): ModelResponseStream = ModelResponseStream()

    @Provides @Singleton
    fun media(gateway: Provider<TdLightGateway>): TelegramMediaStore = TelegramMediaStore(
        configuration.directory.resolve("telegram/files"),
        listOf("profile_photos", "stickers", "thumbnails", "wallpapers").map {
            configuration.directory.resolve("telegram/data/" + it)
        },
    ) { gateway.get().downloadFile(it) }

    @Provides @Singleton
    fun interaction(connection: TelegramConnectionState): TelegramInteraction = TelegramInteraction {
        connection.status =
            it
    }

    @Provides @Singleton
    fun replies(
        settings: SettingsStore,
        telegram: TelegramGateway,
        automated: AutomatedReplyStore,
        generator: PersonalReplyGenerator,
        tracing: ReplyTraceScope,
        diagnostics: DiagnosticLog,
        feedback: ActivityFeedbackStore,
        exclusions: ContextExclusionStore,
    ): ReplyCoordinator = ReplyCoordinator(
        settings,
        telegram,
        automated,
        tracing,
        diagnostics,
        feedback,
        exclusions = exclusions,
        generate = generator::reply,
    )

    @Provides @Singleton
    fun memoryQueue(replies: ReplyCoordinator): MemoryWorkQueue = replies

    @Provides @Singleton
    fun json(): Json = Json { encodeDefaults = true }

    @Provides @Singleton
    fun session(): LocalSession = LocalSession(UUID.randomUUID().toString())
}
