package ru.boltunya.flows

import jakarta.inject.Inject
import ru.boltunya.automation.PersonalReplyGenerator
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult

class FlowEngine @Inject constructor(
    private val store: FlowStore,
    private val template: FlowTemplate,
    private val generator: PersonalReplyGenerator,
    private val validation: FlowValidation,
) {
    fun matches(message: ChatMessage): Boolean = matching(message) != null

    fun execute(message: ChatMessage, rule: ChatRule, history: List<ChatMessage>): FlowExecution? {
        val flow = matching(message) ?: return null
        return run(flow, message, rule, history, generator::reply)
    }

    fun run(
        flow: AutomationFlow,
        message: ChatMessage,
        rule: ChatRule,
        history: List<ChatMessage>,
        generate: (ChatRule, List<ChatMessage>) -> ReplyResult = generator::reply,
    ): FlowExecution {
        validation.validate(flow)
        val values = store.values(flow.id, message.chatId).toMutableMap()
        values["message"] = message.text
        values["sender"] = message.senderName
        values["chatId"] = message.chatId
        values["reply"] = ""
        val root = flow.nodes.single { it.kind == FlowNodeKind.COMMAND }
        val started = System.nanoTime()
        val text = step(flow, root, values, rule, history, generate)
        val data = values.filterKeys { it.startsWith("data.") }
        return FlowExecution(
            flow.id,
            message.chatId,
            ReplyResult(text, (System.nanoTime() - started) / 1_000_000, 0.0),
            data,
        )
    }

    fun commit(execution: FlowExecution) {
        store.commit(execution.flowId, execution.chatId, execution.values)
    }

    private fun matching(message: ChatMessage): AutomationFlow? = store.list().firstOrNull { flow ->
        flow.enabled && (flow.chatIds.isEmpty() || message.chatId in flow.chatIds) &&
            flow.nodes.stream().anyMatch { node ->
                node.kind == FlowNodeKind.COMMAND && when (node.match) {
                    "equals" -> message.text.equals(node.text, ignoreCase = true)
                    "contains" -> message.text.contains(node.text, ignoreCase = true)
                    else -> message.text.startsWith(node.text, ignoreCase = true)
                }
            }
    }

    private fun step(
        flow: AutomationFlow,
        node: FlowNode,
        values: MutableMap<String, String>,
        rule: ChatRule,
        history: List<ChatMessage>,
        generate: (ChatRule, List<ChatMessage>) -> ReplyResult,
    ): String {
        var branch = ""
        when (node.kind) {
            FlowNodeKind.AGENT -> values["reply"] =
                generate(
                    rule.copy(instructions = template.render(node.text, values) + "\n" + rule.instructions),
                    history,
                ).text
            FlowNodeKind.REPLY -> return template.render(node.text, values)
            FlowNodeKind.STORE -> when (node.operation) {
                "get" -> values["reply"] = values["data.${node.key}"].orEmpty()
                "delete" -> values.remove("data.${node.key}")
                else -> values["data.${node.key}"] = template.render(node.text, values)
            }
            FlowNodeKind.CONDITION ->
                branch =
                    (
                        template.render(node.text, values).isNotBlank() &&
                            values["message"].orEmpty().contains(template.render(node.text, values), ignoreCase = true)
                        ).toString()
            FlowNodeKind.COMMAND -> Unit
        }
        val edge =
            flow.edges.firstOrNull {
                it.source == node.id &&
                    (node.kind != FlowNodeKind.CONDITION || it.output == branch)
            }
                ?: return values["reply"].orEmpty()
        val next = flow.nodes.first { it.id == edge.target }
        return step(flow, next, values, rule, history, generate)
    }
}
