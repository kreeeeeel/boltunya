package ru.aigram.ai

import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.ai.model.ModelPolicies
import ru.aigram.ai.model.ModelReplyReader
import ru.aigram.ai.model.SpringAiModelFactory
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.memory.MemoryContext
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.ModelMeasurementStore
import java.nio.file.Files

class AiTestServices(
    baseUrl: String = "http://127.0.0.1:11434",
    prompts: PromptBuilder = PromptBuilder(),
    val tracing: ReplyTraceScope = ReplyTraceScope(),
) {
    val runtime = OllamaRuntime(baseUrl)
    private val resources = ResourceCatalog()
    private val inference = ModelInference()
    val completion = ChatCompletionService(
        SpringAiModelFactory().create(baseUrl),
        ModelPolicies(resources),
        ModelReplyReader(),
        runtime,
        inference,
        ModelMeasurementStore(Files.createTempDirectory("boltunya-sdk-test-")),
        tracing,
    )
    val conversation = ConversationService(
        completion,
        prompts,
        tracing,
    )

    fun installed() = runtime.installed()

    fun summarize(model: String, instruction: String, data: String) = completion.summarize(model, instruction, data)

    fun reply(
        rule: ChatRule,
        history: List<ChatMessage>,
        globalPrompt: String = "",
        styleHistory: List<ChatMessage> = history,
        memory: MemoryContext = MemoryContext(),
    ) = conversation.reply(rule, history, globalPrompt, styleHistory, memory)
}
