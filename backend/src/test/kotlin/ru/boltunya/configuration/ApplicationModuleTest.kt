package ru.boltunya.configuration

import com.google.inject.Guice
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.EngineManager
import ru.boltunya.application.ApplicationRuntime
import ru.boltunya.automation.MemoryWorkQueue
import ru.boltunya.automation.PersonalReplyGenerator
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.domain.ChatRule
import ru.boltunya.exception.impl.ModelNotReadyForAutomationException
import ru.boltunya.http.LocalWebServer
import ru.boltunya.storage.SettingsStore
import ru.boltunya.telegram.TelegramGateway
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class ApplicationModuleTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `both regular and Windows launches honor automatic assistant startup`() {
        SettingsStore(directory).update { it.copy(resumeOnStartup = true, paused = true) }
        listOf(false, true).stream().forEach { windowsStartup ->
            val injector = Guice.createInjector(ApplicationModule(ApplicationConfiguration(directory, windowsStartup)))
            injector.getInstance(ApplicationRuntime::class.java).use {
                assertFalse(injector.getInstance(SettingsStore::class.java).get().paused)
            }
        }
    }

    @Test
    fun `automatic generation rejects a draft only model before accessing history or inference`() {
        val injector = Guice.createInjector(ApplicationModule(ApplicationConfiguration(directory, false)))
        injector.getInstance(ApplicationRuntime::class.java).use {
            val generator = injector.getInstance(PersonalReplyGenerator::class.java)
            assertFailsWith<ModelNotReadyForAutomationException> {
                generator.reply(ChatRule(model = "qwen3:0.6b", useDefaultModel = false), emptyList())
            }
        }
    }

    @Test
    fun `resolves complete dependency graph and shares application services`() {
        val injector = Guice.createInjector(ApplicationModule(ApplicationConfiguration(directory, false)))
        val runtime = injector.getInstance(ApplicationRuntime::class.java)
        runtime.use {
            val settings = injector.getInstance(SettingsStore::class.java)
            assertSame(settings, injector.getInstance(SettingsStore::class.java))
            assertSame(runtime, injector.getInstance(ApplicationRuntime::class.java))
            assertSame(injector.getInstance(EngineManager::class.java), injector.getInstance(EngineManager::class.java))
            assertSame(
                injector.getInstance(TelegramGateway::class.java),
                injector.getInstance(TelegramGateway::class.java),
            )
            assertSame(
                injector.getInstance(ReplyCoordinator::class.java),
                injector.getInstance(ReplyCoordinator::class.java),
            )
            assertSame(
                injector.getInstance(ReplyCoordinator::class.java),
                injector.getInstance(MemoryWorkQueue::class.java),
            )
            assertSame(
                injector.getInstance(LocalWebServer::class.java),
                injector.getInstance(LocalWebServer::class.java),
            )
            assertSame(
                injector.getInstance(PersonalReplyGenerator::class.java),
                injector.getInstance(PersonalReplyGenerator::class.java),
            )
            settings.update { it.copy(globalPrompt = "shared setting") }
            assertEquals("shared setting", injector.getInstance(SettingsStore::class.java).get().globalPrompt)
        }
    }
}
