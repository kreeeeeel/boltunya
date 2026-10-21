package ru.boltunya.configuration

import com.google.inject.AbstractModule
import com.google.inject.Provides
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.serialization.json.Json
import org.springframework.ai.chat.model.ChatModel
import ru.boltunya.ai.ComputeDeviceDiscovery
import ru.boltunya.ai.ConversationService
import ru.boltunya.ai.DownloadWriter
import ru.boltunya.ai.EngineManager
import ru.boltunya.ai.EngineProcessLocator
import ru.boltunya.ai.MessageContextFormatter
import ru.boltunya.ai.ModelCatalog
import ru.boltunya.ai.ModelInference
import ru.boltunya.ai.NvidiaDeviceDiscovery
import ru.boltunya.ai.OllamaDeviceSelector
import ru.boltunya.ai.OllamaInstallation
import ru.boltunya.ai.OllamaModelInventory
import ru.boltunya.ai.OllamaRuntime
import ru.boltunya.ai.PersonalStyleBuilder
import ru.boltunya.ai.PromptBuilder
import ru.boltunya.ai.RegistryBlobDownloader
import ru.boltunya.ai.RegistryModelInstaller
import ru.boltunya.ai.ReplyImageContext
import ru.boltunya.ai.ReplyMediaPlanner
import ru.boltunya.ai.model.ChatCompletionService
import ru.boltunya.ai.model.ModelPolicies
import ru.boltunya.ai.model.ModelReplyReader
import ru.boltunya.ai.model.ModelResponseStream
import ru.boltunya.ai.model.SpringAiModelFactory
import ru.boltunya.ai.profile.ModelCredentialVault
import ru.boltunya.ai.profile.ModelProfileStore
import ru.boltunya.ai.profile.ModelRoutingStore
import ru.boltunya.ai.profile.ModelTargetResolver
import ru.boltunya.ai.profile.ProfileChatOptions
import ru.boltunya.ai.profile.ProfileContextBudget
import ru.boltunya.ai.profile.ProfileModelFactory
import ru.boltunya.ai.research.CurrencyRatesClient
import ru.boltunya.ai.research.ExaMcpSearchClient
import ru.boltunya.ai.research.ExaResultParser
import ru.boltunya.ai.research.FallbackSearchProvider
import ru.boltunya.ai.research.InternetResearcher
import ru.boltunya.ai.research.NewsSearchClient
import ru.boltunya.ai.research.OptionalExaSearchProvider
import ru.boltunya.ai.research.PublicPageReader
import ru.boltunya.ai.research.ResearchFactGuard
import ru.boltunya.ai.research.ResearchLinkPolicy
import ru.boltunya.ai.research.ResearchPlanner
import ru.boltunya.ai.research.ResearchUrl
import ru.boltunya.ai.research.SearchProvider
import ru.boltunya.ai.research.SearchResultSelector
import ru.boltunya.ai.research.WeatherClient
import ru.boltunya.ai.research.WebSearchClient
import ru.boltunya.application.ApplicationRuntime
import ru.boltunya.application.ProcessExit
import ru.boltunya.automation.MemoryWorkQueue
import ru.boltunya.automation.PersonalReplyGenerator
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.automation.ReplyDeliveryPolicy
import ru.boltunya.automation.activity.ReplyTraceScope
import ru.boltunya.diagnostics.DiagnosticLog
import ru.boltunya.http.ApiRouter
import ru.boltunya.http.ExitAction
import ru.boltunya.http.InputValidation
import ru.boltunya.http.LocalSession
import ru.boltunya.http.LocalWebServer
import ru.boltunya.http.MediaResponder
import ru.boltunya.http.RequestReader
import ru.boltunya.http.ResourceResponder
import ru.boltunya.http.ResponseWriter
import ru.boltunya.http.endpoint.ActivityEndpoint
import ru.boltunya.http.endpoint.AppearanceEndpoint
import ru.boltunya.http.endpoint.AttachmentEndpoint
import ru.boltunya.http.endpoint.AuthenticationEndpoint
import ru.boltunya.http.endpoint.ChatEndpoint
import ru.boltunya.http.endpoint.ContextExclusionEndpoint
import ru.boltunya.http.endpoint.DiagnosticsEndpoint
import ru.boltunya.http.endpoint.DraftEndpoint
import ru.boltunya.http.endpoint.EngineEndpoint
import ru.boltunya.http.endpoint.MediaEndpoint
import ru.boltunya.http.endpoint.MemoryEndpoint
import ru.boltunya.http.endpoint.ModelProfilesEndpoint
import ru.boltunya.http.endpoint.PreviewEndpoint
import ru.boltunya.http.endpoint.SearchEndpoint
import ru.boltunya.http.endpoint.SettingsEndpoint
import ru.boltunya.http.endpoint.StateEndpoint
import ru.boltunya.http.endpoint.UploadEndpoint
import ru.boltunya.media.UploadStore
import ru.boltunya.memory.MemoryBatchBuilder
import ru.boltunya.memory.MemoryCoordinator
import ru.boltunya.memory.MemoryProcessor
import ru.boltunya.memory.MemoryStore
import ru.boltunya.memory.MemorySummarizer
import ru.boltunya.memory.OllamaMemorySummarizer
import ru.boltunya.memory.OwnerMemorySummarizer
import ru.boltunya.memory.ParticipantRosterStore
import ru.boltunya.monitoring.MonitoringService
import ru.boltunya.monitoring.NvidiaMonitor
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.ActivityFeedbackStore
import ru.boltunya.storage.AppearanceStore
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.ContextExclusionStore
import ru.boltunya.storage.CredentialStore
import ru.boltunya.storage.MessageArchiveStore
import ru.boltunya.storage.ModelMeasurementStore
import ru.boltunya.storage.SearchCredentialStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.storage.StorageLocations
import ru.boltunya.storage.WritingStyleStore
import ru.boltunya.telegram.MediaDownloadPolicy
import ru.boltunya.telegram.MediaPrefetcher
import ru.boltunya.telegram.StorageClient
import ru.boltunya.telegram.TdLightGateway
import ru.boltunya.telegram.TdLightStorageClient
import ru.boltunya.telegram.TelegramCache
import ru.boltunya.telegram.TelegramCacheStorage
import ru.boltunya.telegram.TelegramChatDirectory
import ru.boltunya.telegram.TelegramChatLoader
import ru.boltunya.telegram.TelegramConnectionState
import ru.boltunya.telegram.TelegramEntityMapper
import ru.boltunya.telegram.TelegramGalleryService
import ru.boltunya.telegram.TelegramGateway
import ru.boltunya.telegram.TelegramHistoryReader
import ru.boltunya.telegram.TelegramHistoryTracker
import ru.boltunya.telegram.TelegramInteraction
import ru.boltunya.telegram.TelegramMediaContent
import ru.boltunya.telegram.TelegramMediaDeliveryTracker
import ru.boltunya.telegram.TelegramMediaSender
import ru.boltunya.telegram.TelegramMediaStore
import ru.boltunya.telegram.TelegramMessageLinks
import ru.boltunya.telegram.TelegramMessageMapper
import ru.boltunya.telegram.TelegramMessageSender
import ru.boltunya.telegram.TelegramMessageView
import ru.boltunya.telegram.TelegramStickerRegistry
import ru.boltunya.telegram.TelegramStickerService
import ru.boltunya.telegram.TelegramTypingStore
import ru.boltunya.telegram.TelegramUpdateHandler
import ru.boltunya.telegram.TelegramWallpaperMapper
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
