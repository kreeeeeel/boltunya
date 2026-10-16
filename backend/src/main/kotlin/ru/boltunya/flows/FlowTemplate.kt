package ru.boltunya.flows

import jakarta.inject.Inject
import ru.boltunya.resources.ResourceCatalog

private const val MAX_RENDERED_TEXT = 4096

class FlowTemplate @Inject constructor() {
    private val pattern = ResourceCatalog().regex("common/flow-template")

    fun render(text: String, values: Map<String, String>): String =
        pattern.replace(text) { values[it.groupValues[1]].orEmpty() }.take(MAX_RENDERED_TEXT)
}
