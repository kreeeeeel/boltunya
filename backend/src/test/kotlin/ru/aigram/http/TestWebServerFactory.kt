package ru.aigram.http

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.util.Modules
import it.tdlight.jni.TdApi
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.ComputeDeviceDiscovery
import ru.aigram.ai.ConversationService
import ru.aigram.ai.EngineManager
import ru.aigram.ai.OllamaRuntime
import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.ai.research.ExaMcpSearchClient
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.configuration.ApplicationModule
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.StorageClient
import ru.aigram.telegram.TelegramGateway
import java.nio.file.Path

class TestWebServerFactory {
    fun create(
        directory: Path,
        settings: SettingsStore,
        telegram: TelegramGateway,
        engine: EngineManager,
        ollama: AiTestServices,
        replies: ReplyCoordinator,
        token: String,
        automated: AutomatedReplyStore = AutomatedReplyStore(directory),
        storageClient: StorageClient = StorageClient { TdApi.StorageStatistics(0, 0, emptyArray()) },
        search: ExaMcpSearchClient? = null,
        diagnostics: DiagnosticLog? = null,
        devices: ComputeDeviceDiscovery? = null,
        onQuit: () -> Unit,
    ): LocalWebServer {
        val overrides = object : AbstractModule() {
            override fun configure() {
                bind(SettingsStore::class.java).toInstance(settings)
                bind(TelegramGateway::class.java).toInstance(telegram)
                bind(EngineManager::class.java).toInstance(engine)
                bind(ConversationService::class.java).toInstance(ollama.conversation)
                bind(OllamaRuntime::class.java).toInstance(ollama.runtime)
                bind(ChatCompletionService::class.java).toInstance(ollama.completion)
                bind(ReplyCoordinator::class.java).toInstance(replies)
                bind(AutomatedReplyStore::class.java).toInstance(automated)
                bind(StorageClient::class.java).toInstance(storageClient)
                if (search != null) bind(ExaMcpSearchClient::class.java).toInstance(search)
                if (diagnostics != null) bind(DiagnosticLog::class.java).toInstance(diagnostics)
                if (devices != null) bind(ComputeDeviceDiscovery::class.java).toInstance(devices)
                bind(LocalSession::class.java).toInstance(LocalSession(token))
                bind(ExitAction::class.java).toInstance(ExitAction(onQuit))
            }
        }
        return Guice.createInjector(
            Modules.override(ApplicationModule(ApplicationConfiguration(directory, false))).with(overrides),
        ).getInstance(LocalWebServer::class.java)
    }
}
