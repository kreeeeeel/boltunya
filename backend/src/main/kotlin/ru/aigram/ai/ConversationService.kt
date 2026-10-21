package ru.aigram.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.content.Media
import org.springframework.stereotype.Component
import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.ai.research.InternetResearcher
import ru.aigram.ai.research.ResearchFactGuard
import ru.aigram.ai.research.ResearchLinkPolicy
import ru.aigram.ai.research.ResearchPlanner
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.ai.ModelReplyPlan
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.ai.research.ResearchContext
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.events.ActivityPhase
import ru.aigram.domain.media.StickerItem
import ru.aigram.domain.memory.MemoryContext
import ru.aigram.exception.impl.EmptyModelReplyException
import ru.aigram.exception.impl.InvalidModelReplyException
import ru.aigram.resources.ResourceCatalog

private const val MEMORY_PLANNER_CHARACTERS = 6000

@Component
class ConversationService(
    private val completion: ChatCompletionService,
    private val prompts: PromptBuilder,
    private val researcher: InternetResearcher,
    private val planner: ResearchPlanner,
    private val links: ResearchLinkPolicy,
    private val facts: ResearchFactGuard,
    private val resources: ResourceCatalog,
    private val tracing: ReplyTraceScope = ReplyTraceScope(),
    private val mediaPlanner: ReplyMediaPlanner = ReplyMediaPlanner(resources),
    private val output: ConversationOutputDecoder = ConversationOutputDecoder(),
    private val formatting: ReplyTextFormatter = ReplyTextFormatter(),
) {
    fun reply(
        rule: ChatRule,
        history: List<ChatMessage>,
        globalPrompt: String = "",
        styleHistory: List<ChatMessage> = history,
        memory: MemoryContext = MemoryContext(),
        images: Map<String, List<Media>> = emptyMap(),
        task: ModelTask = ModelTask.REPLY,
        stickers: List<StickerItem> = emptyList(),
        formatReplies: Boolean = false,
    ): ReplyResult = replyNow(rule, history, globalPrompt, styleHistory, memory, images, task, stickers, formatReplies)

    private fun replyNow(
        rule: ChatRule,
        history: List<ChatMessage>,
        globalPrompt: String,
        styleHistory: List<ChatMessage>,
        memory: MemoryContext,
        images: Map<String, List<Media>>,
        task: ModelTask,
        stickers: List<StickerItem>,
        formatReplies: Boolean = false,
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
        val modelPrompt = completion.instructions(rule, globalPrompt, task)
        val ownerInstructions = modelPrompt + "\n" + rule.instructions
        val instructions = ownerInstructions + "\n" + memoryFacts
        fun generate(
            context: List<Message>,
            format: JsonObject? = null,
            maxTokens: Int = rule.maxTokens,
            generationTask: ModelTask = task,
        ) = completion.generate(rule, context, format, maxTokens, generationTask)
        val research = if (rule.internetAccess && planner.needed(history)) {
            tracing.phase(ActivityPhase.RESEARCH)
            val plan = planner.plan(history, instructions) { context, format ->
                generate(
                    context,
                    format,
                    192,
                    ModelTask.RESEARCH,
                ).text
            }
            tracing.phase(ActivityPhase.SEARCH)
            val found = researcher.lookup(plan)
            if (found.status in setOf("weather", "search", "currency")) {
                planner.remember(history, plan)
            }
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
        val inputTarget = completion.promptTarget(rule, task)
        val reservedTokens = completion.reservedTokens(inputTarget, rule.maxTokens)
        val baseMessages = prompts.build(
            rule,
            history,
            modelPrompt,
            research,
            styleHistory,
            memory,
            images,
            inputTarget,
            reservedTokens,
            formatReplies,
        )
        val messages = if (stickers.isEmpty()) {
            baseMessages
        } else {
            mediaPlanner.messages(baseMessages, stickers)
        }
        val replySchema = if (stickers.isEmpty()) {
            null
        } else {
            mediaPlanner.schema(stickers)
        }
        fun read(text: String): ModelReplyPlan {
            val plan = if (stickers.isEmpty()) {
                ModelReplyPlan(text)
            } else {
                mediaPlanner.read(text, stickers)
            }
            return plan.copy(text = output.decode(plan.text))
        }
        tracing.phase(ActivityPhase.REPLY)
        var response = generate(messages, replySchema)
        var plan = read(response.text)
        var text = plan.text
        if (!facts.matches(text, research, history)) {
            tracing.phase(ActivityPhase.RETRY)
            val retryBase = prompts.build(
                rule,
                history,
                modelPrompt,
                research,
                styleHistory,
                memory,
                images,
                inputTarget,
                reservedTokens,
                formatReplies,
            )
            val precision = resources.prompt(
                "ru/research/retry-precision",
                mapOf("numbers" to research.requiredNumbers.joinToString(", ")),
            )
            val retry =
                listOf(
                    SystemMessage(
                        retryBase.first().text.orEmpty() + "\n" + resources.prompt("ru/conversation/retry") + "\n" +
                            precision,
                    ),
                ) +
                    retryBase.drop(1)
            response = generate(
                if (stickers.isEmpty()) {
                    retry
                } else {
                    mediaPlanner.messages(retry, stickers)
                },
                replySchema,
            )
            plan = read(response.text)
            text = plan.text
        }
        if (text.isBlank() && plan.stickerId.isBlank()) {
            throw EmptyModelReplyException()
        }
        if (!facts.matches(text, research, history)) {
            throw InvalidModelReplyException()
        }
        text = links.format(text, rule, history, research)
        val formatted = formatting.format(text, formatReplies)
        return ReplyResult(
            formatted.text,
            (System.nanoTime() - started) / 1_000_000,
            response.tokensPerSecond,
            completion.modelName(rule, task),
            plan.stickerId,
            formatted.entities,
        )
    }
}
