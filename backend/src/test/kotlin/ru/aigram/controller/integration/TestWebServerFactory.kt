package ru.aigram.controller.integration

import it.tdlight.jni.TdApi
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.ComputeDeviceDiscovery
import ru.aigram.ai.EngineManager
import ru.aigram.application.ExitAction
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.configuration.AigramApplication
import ru.aigram.configuration.ApplicationConfiguration
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.server.LocalSession
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
        diagnostics: DiagnosticLog? = null,
        devices: ComputeDeviceDiscovery? = null,
        onQuit: () -> Unit,
    ): TestWebServer {
        val supplied = linkedMapOf<String, Any>(
            "settingsStore" to settings,
            "tdLightGateway" to telegram,
            "engineManager" to engine,
            "conversationService" to ollama.conversation,
            "ollamaRuntime" to ollama.runtime,
            "chatCompletionService" to ollama.completion,
            "replyCoordinator" to replies,
            "automatedReplyStore" to automated,
            "tdLightStorageClient" to storageClient,
            "localSession" to LocalSession(token),
            "processExit" to ExitAction(onQuit),
        )
        if (diagnostics != null) {
            supplied["diagnosticLog"] = diagnostics
        }
        if (devices != null) {
            supplied["nvidiaDeviceDiscovery"] = devices
        }

        val application = SpringApplicationBuilder(AigramApplication::class.java)
            .web(WebApplicationType.SERVLET)
            .properties(
                "server.address=127.0.0.1",
                "server.port=0",
                "spring.jmx.enabled=false",
            )
            .profiles("tests")
            .registerShutdownHook(false)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .initializers({ context ->
                context.beanFactory.registerSingleton(
                    "applicationConfiguration",
                    ApplicationConfiguration(directory, false),
                )
                context.addBeanFactoryPostProcessor { beanFactory ->
                    val registry = beanFactory as BeanDefinitionRegistry
                    // HTTP tests never start the desktop lifecycle, which owns the supplied services.
                    if (registry.containsBeanDefinition("applicationRuntime")) {
                        registry.getBeanDefinition("applicationRuntime").isLazyInit = true
                    }

                    supplied.entries.stream().forEach { (name, instance) ->
                        if (registry.containsBeanDefinition(name)) {
                            registry.removeBeanDefinition(name)
                        }
                        // These instances are closed by their test callers, rather than owned by Spring.
                        beanFactory.registerSingleton(name, instance)
                    }
                }
            }).run()

        if (application !is ServletWebServerApplicationContext) {
            application.close()
            throw IllegalStateException("HTTP integration tests require the real servlet web server context")
        }
        return TestWebServer(application)
    }
}
