package ru.aigram.memory

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.resources.ResourceCatalog

class OllamaMemorySummarizer @Inject constructor(
    private val completion: ChatCompletionService,
    private val resources: ResourceCatalog,
) : MemorySummarizer,
    OwnerMemorySummarizer {
    override fun fitsOwner(model: String, existing: String, sources: List<MemorySource>): Boolean =
        completion.fitsSummary(model, resources.prompt("ru/memory/owner-summarize"), data(existing, sources))

    override fun fits(model: String, existing: String, sources: List<MemorySource>): Boolean =
        completion.fitsSummary(model, resources.prompt("ru/memory/summarize"), data(existing, sources))

    override fun summarizeOwner(model: String, existing: String, sources: List<MemorySource>): String =
        completion.summarize(
            model,
            resources.prompt("ru/memory/owner-summarize"),
            data(existing, sources.filter { it.speaker == MemorySpeaker.OWNER }),
        )

    override fun summarize(model: String, existing: String, sources: List<MemorySource>): String = completion.summarize(
        model,
        resources.prompt("ru/memory/summarize"),
        data(existing, sources),
    )

    private fun data(existing: String, sources: List<MemorySource>): String = buildJsonObject {
        put("previousMemory", existing)
        put("messages", Json.encodeToJsonElement(sources))
    }.toString()
}
