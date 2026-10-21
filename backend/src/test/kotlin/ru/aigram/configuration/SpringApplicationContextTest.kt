package ru.aigram.configuration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.config.BeanDefinitionCustomizer
import org.springframework.boot.Banner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.server.AbstractConfigurableWebServerFactory
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.support.GenericApplicationContext
import ru.aigram.ai.EngineManager
import ru.aigram.ai.OllamaRuntime
import ru.aigram.application.ApplicationRuntime
import ru.aigram.automation.MemoryWorkQueue
import ru.aigram.automation.PersonalReplyGenerator
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatRule
import ru.aigram.exception.impl.ModelNotReadyForAutomationException
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.memory.MemorySummarizer
import ru.aigram.memory.OllamaMemorySummarizer
import ru.aigram.memory.OwnerMemorySummarizer
import ru.aigram.monitoring.MonitoringService
import ru.aigram.resources.ResourceCatalog
import ru.aigram.server.LocalApiServer
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TdLightGateway
import ru.aigram.telegram.TelegramGateway
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpringApplicationContextTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `command line server properties cannot change the local API origin or context path`() {
        DiNetworkProbe().start().use { network ->
            builder(ApplicationConfiguration(directory, false, network.url))
                .run("--server.address=127.0.0.2", "--server.port=1", "--server.servlet.context-path=/unexpected")
                .use { application ->
                    val factory = application.getBean(AbstractConfigurableWebServerFactory::class.java)
                    val endpoint = URI.create(application.getBean(LocalApiServer::class.java).start())
                    assertEquals("127.0.0.1", factory.address?.hostAddress)
                    assertEquals(0, factory.port)
                    assertEquals("", (application as ServletWebServerApplicationContext).servletContext?.contextPath)
                    assertEquals("127.0.0.1", endpoint.host)
                    assertTrue(endpoint.port > 0)
                    assertEquals(0, network.requests.get())
                }
        }
    }

    @Test
    fun `both regular and Windows launches honor automatic assistant startup`() {
        SettingsStore(directory).update { it.copy(resumeOnStartup = true, paused = true) }
        DiNetworkProbe().start().use { network ->
            listOf(false, true).stream().forEach { windowsStartup ->
                context(ApplicationConfiguration(directory, windowsStartup, network.url)).use { application ->
                    assertFalse(application.getBean(SettingsStore::class.java).get().paused)
                    assertEquals(windowsStartup, application.getBean(ApplicationConfiguration::class.java).startup)
                }
            }
            assertEquals(0, network.requests.get())
        }
    }

    @Test
    fun `automatic generation rejects a draft only model before accessing history or inference`() {
        DiNetworkProbe().start().use { network ->
            context(ApplicationConfiguration(directory, false, network.url)).use { application ->
                val generator = application.getBean(PersonalReplyGenerator::class.java)
                assertFailsWith<ModelNotReadyForAutomationException> {
                    generator.reply(ChatRule(model = "qwen3:0.6b", useDefaultModel = false), emptyList())
                }
            }
            assertEquals(0, network.requests.get())
        }
    }

    @Test
    fun `real Boot context resolves complete graph with shared interface singletons and supplied configuration`() {
        DiNetworkProbe().start().use { network ->
            val supplied = ApplicationConfiguration(directory, false, network.url)
            context(supplied).use { application ->
                val runtime = application.getBean(ApplicationRuntime::class.java)
                val settings = application.getBean(SettingsStore::class.java)
                assertSame(supplied, application.getBean(ApplicationConfiguration::class.java))
                assertEquals(directory, application.getBean(Path::class.java))
                assertSame(settings, application.getBean(SettingsStore::class.java))
                assertSame(runtime, application.getBean(ApplicationRuntime::class.java))
                assertSame(
                    application.getBean(EngineManager::class.java),
                    application.getBean(EngineManager::class.java),
                )
                assertSame(
                    application.getBean(TdLightGateway::class.java),
                    application.getBean(TelegramGateway::class.java),
                )
                assertSame(
                    application.getBean(ReplyCoordinator::class.java),
                    application.getBean(MemoryWorkQueue::class.java),
                )
                val summarizer = application.getBean(OllamaMemorySummarizer::class.java)
                assertSame(summarizer, application.getBean(MemorySummarizer::class.java))
                assertSame(summarizer, application.getBean(OwnerMemorySummarizer::class.java))
                assertSame(
                    application.getBean(LocalApiServer::class.java),
                    application.getBean(LocalApiServer::class.java),
                )
                assertSame(
                    application.getBean(PersonalReplyGenerator::class.java),
                    application.getBean(PersonalReplyGenerator::class.java),
                )
                assertSame(
                    application.getBean(OllamaRuntime::class.java),
                    application.getBean(OllamaRuntime::class.java),
                )
                settings.update { it.copy(globalPrompt = "shared setting") }
                assertEquals("shared setting", application.getBean(SettingsStore::class.java).get().globalPrompt)
            }
            assertEquals(0, network.requests.get())
        }
    }

    @Test
    fun `creating and repeatedly closing the context does not start runtime Telegram monitoring or inference`() {
        DiNetworkProbe().start().use { network ->
            val existingThreads = serviceThreads().stream().map { it.threadId() }.collect(Collectors.toSet())
            val application = context(ApplicationConfiguration(directory, true, network.url))
            val runtime = application.getBean(ApplicationRuntime::class.java)
            val replies = application.getBean(ReplyCoordinator::class.java)
            val ownedThreads = serviceThreads().stream().filter { it.threadId() !in existingThreads }.toList()
            try {
                assertTrue(ownedThreads.isNotEmpty(), "The real shared queue must be instantiated")
                assertFalse(Files.exists(directory.resolve("app.lock")))
                assertFalse(Files.exists(directory.resolve("launch.url")))
                assertFalse(Files.exists(directory.resolve("memory/MEMORY.md")))
                assertEquals("disconnected", application.getBean(TelegramGateway::class.java).status().state)
                assertTrue(application.getBean(MonitoringService::class.java).history().isEmpty())
                assertEquals(0, replies.queue().queued)
                assertEquals(0, network.requests.get())
            } finally {
                application.close()
                application.close()
            }
            assertFalse(application.isActive)
            assertFailsWith<ReplyCancelledException> {
                replies.enqueueDraft("42", "synthetic") { ReplyResult("Unused", 0, 0.0) }
            }
            runtime.close()
            runtime.close()
            assertTrue(
                IntStream.range(0, 300).anyMatch {
                    if (ownedThreads.stream().noneMatch(Thread::isAlive)) {
                        true
                    } else {
                        TimeUnit.MILLISECONDS.sleep(10)
                        false
                    }
                },
                "Closing the context must stop its shared queue executors",
            )
            assertEquals(0, network.requests.get())
        }
    }

    @Test
    fun `failed refresh closes already created resources without instantiating the runtime shutdown owner`() {
        DiNetworkProbe().start().use { network ->
            val existingThreads = serviceThreads().stream().map { it.threadId() }.collect(Collectors.toSet())
            val createdContext = AtomicReference<ConfigurableApplicationContext>()
            val createdServer = AtomicReference<LocalApiServer>()
            val createdQueue = AtomicReference<ReplyCoordinator>()
            val ownedThreads = AtomicReference<List<Thread>>(emptyList())
            val listener = AtomicReference("")
            val runtimeAbsent = AtomicBoolean()
            try {
                val failure = assertFailsWith<RuntimeException> {
                    builder(ApplicationConfiguration(directory, false, network.url))
                        .initializers({ application ->
                            createdContext.set(application)
                            application.addBeanFactoryPostProcessor { factory ->
                                factory.getBeanDefinition("applicationRuntime").isLazyInit = true
                            }
                            (application as GenericApplicationContext).registerBean(
                                "syntheticBootstrapFailure",
                                FailedRefreshListener::class.java,
                                Supplier {
                                    FailedRefreshListener { ready ->
                                        runtimeAbsent.set(!ready.beanFactory.containsSingleton("applicationRuntime"))
                                        val server = ready.getBean(LocalApiServer::class.java)
                                        createdServer.set(server)
                                        createdQueue.set(ready.getBean(ReplyCoordinator::class.java))
                                        listener.set(server.start())
                                        ownedThreads.set(
                                            serviceThreads().stream().filter {
                                                it.threadId() !in existingThreads
                                            }.toList(),
                                        )
                                        throw IllegalStateException(
                                            "Synthetic failure after queue and HTTP initialization",
                                        )
                                    }
                                },
                                BeanDefinitionCustomizer {
                                    it.setDependsOn("replyCoordinator", "localApiServer")
                                },
                            )
                        })
                        .run()
                }
                assertTrue(
                    generateSequence<Throwable>(failure) { it.cause }.any {
                        it.message == "Synthetic failure after queue and HTTP initialization"
                    },
                    "The failure must occur after the real servlet HTTP listener has started",
                )
                assertTrue(runtimeAbsent.get(), "ApplicationRuntime must not mask partial refresh cleanup")
                assertTrue(ownedThreads.get().isNotEmpty())
                assertTrue(
                    IntStream.range(0, 300).anyMatch {
                        if (ownedThreads.get().stream().noneMatch(Thread::isAlive)) {
                            true
                        } else {
                            TimeUnit.MILLISECONDS.sleep(10)
                            false
                        }
                    },
                    "Failed refresh must stop the already created queue executors",
                )
                assertFailsWith<ReplyCancelledException> {
                    createdQueue.get().enqueueDraft("42", "synthetic") { ReplyResult("Unused", 0, 0.0) }
                }
                ServerSocket().use { socket ->
                    socket.bind(InetSocketAddress("127.0.0.1", URI.create(listener.get()).port))
                }
                assertFalse(Files.exists(directory.resolve("app.lock")))
                assertFalse(Files.exists(directory.resolve("launch.url")))
                assertEquals(0, network.requests.get())
            } finally {
                createdContext.get()?.close()
                createdServer.get()?.close()
                createdQueue.get()?.close()
            }
        }
    }

    @Test
    fun `Spring ObjectProvider resolves a lazy dependency only when requested and preserves singleton identity`() {
        AnnotationConfigApplicationContext().use { application ->
            application.registerBean(
                "lazyResources",
                ResourceCatalog::class.java,
                BeanDefinitionCustomizer { it.isLazyInit = true },
            )
            application.registerBean(LazyProviderProbe::class.java, *emptyArray<BeanDefinitionCustomizer>())
            application.refresh()
            val probe = application.getBean(LazyProviderProbe::class.java)
            assertFalse(application.beanFactory.containsSingleton("lazyResources"))
            val resources = probe.resources.getObject()
            assertTrue(application.beanFactory.containsSingleton("lazyResources"))
            assertSame(resources, probe.resources.getObject())
            assertSame(resources, application.getBean(ResourceCatalog::class.java))
        }
    }

    private fun context(configuration: ApplicationConfiguration): ConfigurableApplicationContext =
        builder(configuration).run()

    private fun builder(configuration: ApplicationConfiguration): SpringApplicationBuilder =
        SpringApplicationBuilder(AigramApplication::class.java)
            .web(WebApplicationType.SERVLET)
            .properties("server.address=127.0.0.1", "server.port=0", "spring.jmx.enabled=false")
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .registerShutdownHook(false)
            .profiles("tests")
            .initializers({ application ->
                application.beanFactory.registerSingleton("applicationConfiguration", configuration)
            })

    private fun serviceThreads(): List<Thread> = Thread.getAllStackTraces().keys.stream()
        .filter { !it.isDaemon && it.name.startsWith("pool-") }.toList()
}
