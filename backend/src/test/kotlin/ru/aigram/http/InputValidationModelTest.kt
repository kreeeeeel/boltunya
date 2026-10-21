package ru.aigram.http

import org.junit.jupiter.api.Test
import ru.aigram.ai.ModelCatalog
import ru.aigram.exception.impl.ModelNotReadyForAutomationException
import ru.aigram.exception.impl.UnknownModelException
import kotlin.test.assertFailsWith

class InputValidationModelTest {
    @Test
    fun `observed native installs are selectable but unknown and restricted models retain their validation`() {
        val catalog = ModelCatalog()
        InputValidation(catalog).use { validation ->
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
