package ru.boltunya.ai

import ru.boltunya.ai.model.ChatCompletionService
import ru.boltunya.ai.model.ModelPolicies
import ru.boltunya.ai.model.ModelReplyReader
import ru.boltunya.ai.model.SpringAiModelFactory
import ru.boltunya.ai.research.InternetResearcher
import ru.boltunya.ai.research.ResearchLinkPolicy
import ru.boltunya.ai.research.ResearchPlanner
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.memory.MemoryContext
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.storage.ModelMeasurementStore
import java.nio.file.Files

class AiTestServices(
    baseUrl: String = "http://127.0.0.1:11434",
    prompts: PromptBuilder = PromptBuilder(),
    researcher: InternetResearcher = InternetResearcher(),
    planner: ResearchPlanner = ResearchPlanner(),
) {
    val runtime = OllamaRuntime(baseUrl)
    private val resources = ResourceCatalog()
    private val inference = ModelInference()
    val completion = ChatCompletionService(
        SpringAiModelFactory().create(baseUrl),
        ModelPolicies(resources),
        ModelReplyReader(resources),
        runtime,
        inference,
        ModelMeasurementStore(Files.createTempDirectory("boltunya-sdk-test-")),
    )
    val conversation = ConversationService(
        completion,
        prompts,
        ReplyQualityGuard(),
        researcher,
        planner,
        inference,
        ResearchLinkPolicy(),
        resources,
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
