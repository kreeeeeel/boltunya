package ru.aigram.ai

import kotlinx.serialization.json.JsonObject
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.content.Media
import org.springframework.stereotype.Component
import ru.aigram.ai.model.ChatCompletionService
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.ai.ModelReplyPlan
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.events.ActivityPhase
import ru.aigram.domain.media.StickerItem
import ru.aigram.domain.memory.MemoryContext
import ru.aigram.exception.impl.EmptyModelReplyException
import ru.aigram.resources.ResourceCatalog

@Component
class ConversationService(
    private val completion: ChatCompletionService,
    private val prompts: PromptBuilder,
    private val tracing: ReplyTraceScope = ReplyTraceScope(),
    private val mediaPlanner: ReplyMediaPlanner = ReplyMediaPlanner(ResourceCatalog()),
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
        val modelPrompt = completion.instructions(rule, globalPrompt, task)
        fun generate(
            context: List<Message>,
            format: JsonObject? = null,
            maxTokens: Int = rule.maxTokens,
            generationTask: ModelTask = task,
        ) = completion.generate(rule, context, format, maxTokens, generationTask)
        val inputTarget = completion.promptTarget(rule, task)
        val reservedTokens = completion.reservedTokens(inputTarget, rule.maxTokens)
        val baseMessages = prompts.build(
            rule,
            history,
            modelPrompt,
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
        val response = generate(messages, replySchema)
        val plan = read(response.text)
        val text = plan.text
        if (text.isBlank() && plan.stickerId.isBlank()) {
            throw EmptyModelReplyException()
        }
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
