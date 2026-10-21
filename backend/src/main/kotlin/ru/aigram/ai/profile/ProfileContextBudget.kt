package ru.aigram.ai.profile

import jakarta.inject.Inject
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.stereotype.Component
import ru.aigram.ai.ModelCatalog
import ru.aigram.ai.prompt.PromptTokenEstimator
import ru.aigram.exception.impl.ModelContextLimitException
import java.util.stream.IntStream

private const val MAX_AUTOMATIC_CONTEXT = 32768
private const val MAX_WINDOW_EXPANSIONS = 24L

/** Keeps system instructions, the latest message and selected images; only older text history may be removed. */
@Component
class ProfileContextBudget @Inject constructor(
    private val tokens: PromptTokenEstimator,
    private val catalog: ModelCatalog = ModelCatalog(),
) {
    constructor() : this(PromptTokenEstimator())

    fun maximum(target: ResolvedModelTarget): Int = if (target.automaticContext) {
        minOf(MAX_AUTOMATIC_CONTEXT, catalog.info(target.model)?.contextTokens ?: target.options.contextWindow)
    } else {
        target.options.contextWindow
    }

    /** Automatic native windows grow only as needed, within the catalog and a bounded local resource budget. */
    fun configure(
        target: ResolvedModelTarget,
        messages: List<Message>,
        reservedOutputTokens: Int,
    ): ResolvedModelTarget {
        if (!target.automaticContext || target.provider != ModelProvider.OLLAMA) return target
        val required = messages.stream().mapToLong { tokens.estimate(it, target.model, target.tokenEncoding).toLong() }
            .sum() + reservedOutputTokens
        val ceiling = maximum(target)
        val window = IntStream.iterate(minOf(target.options.contextWindow, ceiling)) { minOf(it * 2, ceiling) }
            .limit(MAX_WINDOW_EXPANSIONS).filter { it >= required || it == ceiling }.findFirst().orElse(ceiling)
        return target.copy(options = target.options.copy(contextWindow = window))
    }

    fun fits(target: ResolvedModelTarget, messages: List<Message>, reservedOutputTokens: Int): Boolean =
        messages.stream().mapToLong { tokens.estimate(it, target.model, target.tokenEncoding).toLong() }.sum() +
            reservedOutputTokens <= maximum(target)

    fun reserve(target: ResolvedModelTarget, outputTokens: Int): Int = minOf(
        target.options.maxOutputTokens,
        outputTokens + if (target.capabilities.reasoning) target.options.reasoningTokens else 0,
    )

    fun select(
        target: ResolvedModelTarget,
        messages: List<Message>,
        outputTokens: Int,
        reservedOutputTokens: Int = reserve(target, outputTokens),
    ): List<Message> {
        if (messages.isEmpty()) return messages
        val costs = messages.map { tokens.estimate(it, target.model, target.tokenEncoding) }
        val last = messages.indexOfLast { it !is SystemMessage }
        fun mandatory(index: Int): Boolean = messages[index] is SystemMessage || index == last ||
            (messages[index] as? UserMessage)?.media?.isNotEmpty() == true
        val fixed = IntStream.range(0, messages.size).filter { mandatory(it) }
            .map { costs[it] }.sum()
        val available = target.options.contextWindow - reservedOutputTokens - fixed
        val required = fixed + reservedOutputTokens
        if (available < 0) {
            throw ModelContextLimitException(required, target.options.contextWindow)
        }
        if (last < 0) return messages
        val suffix = IntArray(messages.size + 1)
        IntStream.range(0, messages.size).map { messages.lastIndex - it }.forEach { index ->
            suffix[index] = suffix[index + 1] + if (mandatory(index)) 0 else costs[index]
        }
        val first = IntStream.rangeClosed(0, last).filter { suffix[it] <= available }.findFirst().orElse(last)
        return IntStream.range(0, messages.size).filter { it >= first || mandatory(it) }
            .mapToObj { messages[it] }.toList()
    }
}
