package ru.boltunya.ai

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import ru.boltunya.ai.model.ChatCompletionService
import ru.boltunya.ai.research.InternetResearcher
import ru.boltunya.ai.research.ResearchContext
import ru.boltunya.ai.research.ResearchFactGuard
import ru.boltunya.ai.research.ResearchLinkPolicy
import ru.boltunya.ai.research.ResearchPlanner
import ru.boltunya.automation.activity.ActivityPhase
import ru.boltunya.automation.activity.ReplyTraceScope
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.impl.EmptyModelReplyException
import ru.boltunya.exception.impl.InvalidModelReplyException
import ru.boltunya.exception.impl.RepetitiveModelReplyException
import ru.boltunya.memory.MemoryContext
import ru.boltunya.resources.ResourceCatalog

private const val MEMORY_PLANNER_CHARACTERS = 6000

class ConversationService @Inject constructor(
    private val completion: ChatCompletionService,
    private val prompts: PromptBuilder,
    private val quality: ReplyQualityGuard,
    private val researcher: InternetResearcher,
    private val planner: ResearchPlanner,
    private val inference: ModelInference,
    private val links: ResearchLinkPolicy,
    private val facts: ResearchFactGuard,
    private val resources: ResourceCatalog,
    private val tracing: ReplyTraceScope = ReplyTraceScope(),
) {
    fun reply(
        rule: ChatRule,
        history: List<ChatMessage>,
        globalPrompt: String = "",
        styleHistory: List<ChatMessage> = history,
        memory: MemoryContext = MemoryContext(),
    ): ReplyResult = inference.foreground { replyNow(rule, history, globalPrompt, styleHistory, memory) }

    private fun replyNow(
        rule: ChatRule,
        history: List<ChatMessage>,
        globalPrompt: String,
        styleHistory: List<ChatMessage>,
        memory: MemoryContext,
    ): ReplyResult {
        val started = System.nanoTime()
        val memoryFacts = if (memory.isEmpty()) {
            ""
        } else {
            resources.prompt(
                "ru/memory/context",
                mapOf("memory" to Json.encodeToString(memory.limited(MEMORY_PLANNER_CHARACTERS))),
            )
        }
        val ownerInstructions = globalPrompt + "\n" + rule.instructions
        val instructions = ownerInstructions + "\n" + memoryFacts
        fun generate(context: List<Message>, format: JsonObject? = null, maxTokens: Int = rule.maxTokens) =
            completion.generate(rule, context, format, maxTokens)
        val research = if (rule.internetAccess && planner.needed(history)) {
            tracing.phase(ActivityPhase.RESEARCH)
            val plan = planner.plan(history, instructions) { context, format ->
                generate(
                    context,
                    format,
                    192,
                ).text
            }
            tracing.phase(ActivityPhase.SEARCH)
            val found = researcher.lookup(plan)
            if (found.status in setOf("weather", "search", "currency")) planner.remember(history, plan)
            found
        } else {
            ResearchContext()
        }
        if (research.status == "unavailable") {
            return ReplyResult(
                resources.prompt("ru/research/no-answer"),
                (System.nanoTime() - started) / 1_000_000,
                0.0,
            )
        }
        val messages = prompts.build(rule, history, globalPrompt, research, styleHistory, memory)
        tracing.phase(ActivityPhase.REPLY)
        var response = generate(messages)
        var text = response.text
        if (quality.needsRetry(
                text,
                history,
                ownerInstructions,
                styleHistory,
                compareSimilarReplies =
                research.status == "none",
            ) || !facts.matches(text, research, history)
        ) {
            tracing.phase(ActivityPhase.RETRY)
            val retryBase = prompts.build(
                rule.copy(matchMyStyle = false),
                history,
                globalPrompt,
                research,
                emptyList(),
                memory,
            )
            val precision = if (facts.matches(text, research, history)) {
                ""
            } else {
                resources.prompt(
                    "ru/research/retry-precision",
                    mapOf("numbers" to research.requiredNumbers.joinToString(", ")),
                )
            }
            val retry =
                listOf(
                    SystemMessage(
                        retryBase.first().text.orEmpty() + "\n" + resources.prompt("ru/conversation/retry") + "\n" +
                            precision,
                    ),
                ) +
                    retryBase.drop(1)
            response = generate(retry)
            text = response.text
        }
        if (text.isBlank()) {
            throw EmptyModelReplyException()
        }
        if (quality.containsInternalAnalysis(text)) throw InvalidModelReplyException()
        if (!facts.matches(text, research, history)) throw InvalidModelReplyException()
        if (quality.needsRetry(
                text,
                history,
                ownerInstructions,
                styleHistory,
                compareSimilarReplies =
                research.status == "none",
            )
        ) {
            throw RepetitiveModelReplyException()
        }
        text = links.format(text, rule, history, research)
        return ReplyResult(
            text,
            (System.nanoTime() - started) / 1_000_000,
            response.tokensPerSecond,
        )
    }
}
