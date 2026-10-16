package ru.boltunya.configuration

import com.google.inject.Guice
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.ai.EngineManager
import ru.boltunya.application.ApplicationRuntime
import ru.boltunya.automation.PersonalReplyGenerator
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.http.LocalWebServer
import ru.boltunya.storage.SettingsStore
import ru.boltunya.telegram.TelegramGateway
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ApplicationModuleTest {
    @TempDir
    lateinit var directory: Path

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
