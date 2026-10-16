package ru.boltunya.flows

import jakarta.inject.Inject
import ru.boltunya.exception.InvalidInputException
import ru.boltunya.exception.impl.CyclicFlowException
import ru.boltunya.exception.impl.InvalidFlowConnectionsException
import ru.boltunya.exception.impl.InvalidFlowDefinitionException
import ru.boltunya.exception.impl.InvalidFlowNodeException
import ru.boltunya.resources.ResourceCatalog

private const val MAX_FLOW_NODES = 64
private const val MAX_FLOW_TEXT = 4000
private const val MAX_FLOW_NAME = 80

class FlowValidation @Inject constructor() {
    private val resources = ResourceCatalog()

    fun validate(flow: AutomationFlow) {
        if (!resources.regex("common/flow-id").matches(flow.id) || flow.name.isBlank() ||
            flow.name.length > MAX_FLOW_NAME ||
            flow.nodes.isEmpty() || flow.nodes.size > MAX_FLOW_NODES || flow.edges.size > MAX_FLOW_NODES * 2 ||
            flow.nodes.stream().map { it.id }.distinct().count() != flow.nodes.size.toLong() ||
            flow.nodes.stream().filter { it.kind == FlowNodeKind.COMMAND }.count() != 1L
        ) {
            throw InvalidFlowDefinitionException()
        }
        val ids = flow.nodes.stream().map { it.id }.toList().toSet()
        if (flow.nodes.stream().anyMatch {
                !resources.regex("common/flow-id").matches(it.id) ||
                    it.text.length > MAX_FLOW_TEXT ||
                    it.key.length > MAX_FLOW_NAME ||
                    !it.x.isFinite() ||
                    !it.y.isFinite()
            } ||
            flow.edges.stream().anyMatch { it.source !in ids || it.target !in ids || it.source == it.target }
        ) {
            throw InvalidFlowConnectionsException()
        }
        val visited = mutableSetOf<String>()
        flow.nodes.stream().forEach { visit(it.id, flow, emptySet(), visited) }
        flow.nodes.stream().forEach { node ->
            val outgoing = flow.edges.filter { it.source == node.id }
            if (outgoing.groupBy { it.output }.values.stream().anyMatch { it.size > 1 } ||
                node.kind != FlowNodeKind.CONDITION && outgoing.size > 1 ||
                node.kind == FlowNodeKind.REPLY && outgoing.isNotEmpty() ||
                node.kind == FlowNodeKind.STORE &&
                (
                    !resources.regex("common/flow-key").matches(node.key) ||
                        node.operation !in setOf("get", "set", "delete")
                    ) ||
                node.kind == FlowNodeKind.COMMAND &&
                (node.text.isBlank() || node.match !in setOf("prefix", "equals", "contains"))
            ) {
                throw InvalidFlowNodeException()
            }
        }
    }

    private fun visit(id: String, flow: AutomationFlow, path: Set<String>, visited: MutableSet<String>) {
        if (id in path) throw CyclicFlowException()
        if (id in visited) return
        flow.edges.stream().filter { it.source == id }.forEach { visit(it.target, flow, path + id, visited) }
        visited.add(id)
    }
}
