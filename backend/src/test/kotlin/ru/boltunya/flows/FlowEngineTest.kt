package ru.boltunya.flows

import com.google.inject.Guice
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.boltunya.application.ApplicationRuntime
import ru.boltunya.configuration.ApplicationConfiguration
import ru.boltunya.configuration.ApplicationModule
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.InvalidInputException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowEngineTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `command agent reply graph preserves isolated JSON values only after commit`() {
        val injector = Guice.createInjector(ApplicationModule(ApplicationConfiguration(directory, false)))
        injector.getInstance(ApplicationRuntime::class.java).use {
            val store = injector.getInstance(FlowStore::class.java)
            val engine = injector.getInstance(FlowEngine::class.java)
            val flow = AutomationFlow(
                "test-flow",
                "Test",
                true,
                listOf("42"),
                listOf(
                    FlowNode("start", FlowNodeKind.COMMAND, "/hello"),
                    FlowNode("store", FlowNodeKind.STORE, "{{message}}", "last"),
                    FlowNode("agent", FlowNodeKind.AGENT, "Тема: {{data.last}}"),
                    FlowNode("reply", FlowNodeKind.REPLY, "{{sender}}: {{reply}}"),
                ),
                listOf(
                    FlowEdge("1", "start", "store"),
                    FlowEdge("2", "store", "agent"),
                    FlowEdge("3", "agent", "reply"),
                ),
            )
            store.save(flow)
            val message = ChatMessage("1", "42", "/hello news", false, 0, senderName = "Alex")
            assertTrue(engine.matches(message))
            assertFalse(engine.matches(message.copy(chatId = "43")))
            val execution = engine.run(flow, message, ChatRule(), listOf(message)) { rule, _ ->
                assertTrue(rule.instructions.contains("/hello news"))
                ReplyResult("готово", 1, 0.0)
            }
            assertEquals("Alex: готово", execution.reply.text)
            assertFalse(Files.exists(directory.resolve("flow-data.json")))
            engine.commit(execution)
            assertEquals("/hello news", store.values(flow.id, "42")["data.last"])
            assertTrue(store.values(flow.id, "43").isEmpty())
            assertEquals(flow, FlowStore(directory, FlowValidation()).list().single())
        }
    }

    @Test
    fun `rejects cyclic graphs and never expands template commands recursively`() {
        val flow =
            AutomationFlow(
                "test-flow",
                "Test",
                nodes = listOf(FlowNode("a", FlowNodeKind.COMMAND, "/start"), FlowNode("b", FlowNodeKind.AGENT)),
                edges = listOf(FlowEdge("1", "a", "b"), FlowEdge("2", "b", "a")),
            )
        assertFailsWith<InvalidInputException> { FlowValidation().validate(flow) }
        assertEquals(
            "{{secret}}",
            FlowTemplate().render(
                "{{message}}",
                mapOf(
                    "message" to "{{secret}}",
                    "secret" to "hidden",
                ),
            ),
        )
    }
}
