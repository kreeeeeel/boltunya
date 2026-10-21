package ru.aigram.controller.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.ai.AiTestServices
import ru.aigram.ai.EngineManager
import ru.aigram.ai.ModelCatalog
import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.exception.impl.ModelNotReadyForAutomationException
import ru.aigram.exception.impl.UnknownModelException
import ru.aigram.storage.SettingsStore
import ru.aigram.validation.InputValidation
import java.nio.file.Path
import kotlin.test.assertFailsWith

class InputValidationModelTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `observed native installs are selectable but unknown and restricted models retain their validation`() {
        val settings = SettingsStore(directory)
        val gateway = FakeTelegramGateway()
        val ai = AiTestServices("http://127.0.0.1:1")
        EngineManager(directory, ai.runtime).use { engine ->
            ReplyCoordinator(settings, gateway) { _, _ -> ReplyResult("unused", 0, 0.0) }.use { replies ->
                TestWebServerFactory().create(directory, settings, gateway, engine, ai, replies, "session") {
                }.use { server ->
                    val validation = server.bean(InputValidation::class.java)
                    val catalog = server.bean(ModelCatalog::class.java)
                    assertFailsWith<UnknownModelException> { validation.model("custom:local", forAutomation = true) }
                    catalog.installed(setOf("custom:local", "gemma3:latest"))
                    validation.model("custom:local", forAutomation = true)
                    validation.model("gemma3:latest", forAutomation = true)
                    assertFailsWith<ModelNotReadyForAutomationException> {
                        validation.model("qwen3:0.6b", forAutomation = true)
                    }
                    assertFailsWith<UnknownModelException> { validation.model("unseen:remote") }
                }
            }
        }
    }
}
