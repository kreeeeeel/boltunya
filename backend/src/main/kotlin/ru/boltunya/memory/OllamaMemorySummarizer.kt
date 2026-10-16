package ru.boltunya.memory

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import ru.boltunya.ai.OllamaClient
import ru.boltunya.resources.ResourceCatalog

class OllamaMemorySummarizer @Inject constructor(
    private val ollama: OllamaClient,
    private val resources: ResourceCatalog,
) : MemorySummarizer,
    OwnerMemorySummarizer {
    override fun summarizeOwner(model: String, existing: String, sources: List<MemorySource>): String =
        ollama.summarize(
            model,
            resources.prompt("ru/memory/owner-summarize"),
            buildJsonObject {
                put("previousMemory", existing)
                put("messages", Json.encodeToJsonElement(sources.filter { it.speaker == MemorySpeaker.OWNER }))
            }.toString(),
        )

    override fun summarize(model: String, existing: String, sources: List<MemorySource>): String = ollama.summarize(
        model,
        resources.prompt("ru/memory/summarize"),
        buildJsonObject {
            put("previousMemory", existing)
            put("messages", Json.encodeToJsonElement(sources))
        }.toString(),
    )
}
