package ru.aigram.ai.profile

import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import org.springframework.core.io.ByteArrayResource
import org.springframework.util.MimeTypeUtils
import ru.aigram.ai.prompt.PromptTokenEstimator
import ru.aigram.domain.ai.profile.ResolvedModelTarget
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutomaticContextBudgetTest {
    private val budget = ProfileContextBudget()
    private val target = ResolvedModelTarget(
        model = "deepseek-r1:8b",
        baseUrl = "http://localhost",
        remote = false,
        automaticContext = true,
    )

    @Test
    fun `automatic native context expands for instructions memory and reasoning without changing saved preferences`() {
        val messages = listOf(SystemMessage("Подтвержденный факт о переписке. ".repeat(650)), UserMessage("Привет"))
        val configured = budget.configure(target, messages, 2304)
        assertTrue(configured.options.contextWindow > 8192)
        assertTrue(configured.options.contextWindow <= 32768)
        assertEquals(8192, target.options.contextWindow)
        assertEquals(messages, budget.select(configured, messages, 256, 2304))
    }

    @Test
    fun `explicit and unknown model limits never expand automatically`() {
        val messages = listOf(SystemMessage("History ".repeat(100000)), UserMessage("Latest"))
        assertEquals(
            8192,
            budget.configure(target.copy(automaticContext = false), messages, 2304).options.contextWindow,
        )
        assertEquals(8192, budget.configure(target.copy(model = "unknown"), messages, 2304).options.contextWindow)
        assertEquals(32768, budget.configure(target, messages, 2304).options.contextWindow)
    }

    @Test
    fun `all album images remain mandatory when older text history is removed`() {
        val image = Media(MimeTypeUtils.IMAGE_PNG, ByteArrayResource(byteArrayOf(1)))
        val first = UserMessage.builder().text("First photo").media(image).build()
        val latest = UserMessage.builder().text("Second photo").media(image).build()
        val system = SystemMessage("Instructions")
        val messages = listOf(system, UserMessage("Old history ".repeat(10000)), first, latest)
        val tokens = PromptTokenEstimator()
        val fixed = listOf(system, first, latest).sumOf { tokens.estimate(it, target.model) }
        val limited = target.copy(automaticContext = false, options = target.options.copy(contextWindow = fixed + 256))
        assertEquals(listOf(system, first, latest), budget.select(limited, messages, 256, 256))
    }
}
