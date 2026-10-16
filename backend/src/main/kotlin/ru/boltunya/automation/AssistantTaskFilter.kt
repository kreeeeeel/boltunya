package ru.boltunya.automation

import ru.boltunya.resources.ResourceCatalog

private val resources = ResourceCatalog()

class AssistantTaskFilter {
    private val instruction = resources.regex("intent/assistant-instruction")
    private val task = resources.regex("intent/assistant-task")

    fun ignores(text: String): Boolean = instruction.containsMatchIn(text) && task.containsMatchIn(text)
}
