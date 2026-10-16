package ru.boltunya.http

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.util.Modules
import it.tdlight.jni.TdApi
import ru.boltunya.ai.AiTestServices
import ru.boltunya.ai.ConversationService
import ru.boltunya.ai.EngineManager
import ru.boltunya.ai.OllamaRuntime
import ru.boltunya.ai.model.ChatCompletionService
import ru.boltunya.ai.research.ExaMcpSearchClient
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.configuration.ApplicationConfiguration
import ru.boltunya.configuration.ApplicationModule
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.telegram.StorageClient
import ru.boltunya.telegram.TelegramGateway
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
                bind(LocalSession::class.java).toInstance(LocalSession(token))
                bind(ExitAction::class.java).toInstance(ExitAction(onQuit))
            }
        }
        return Guice.createInjector(
            Modules.override(ApplicationModule(ApplicationConfiguration(directory, false))).with(overrides),
        ).getInstance(LocalWebServer::class.java)
    }
}
