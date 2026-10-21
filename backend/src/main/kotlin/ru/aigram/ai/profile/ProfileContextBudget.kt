package ru.aigram.ai.profile

import jakarta.inject.Inject
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import ru.aigram.ai.prompt.PromptTokenEstimator
import ru.aigram.exception.impl.ModelContextLimitException
import java.util.stream.IntStream

/** Keeps every system instruction and the latest message; only older history may be removed. */
class ProfileContextBudget @Inject constructor(private val tokens: PromptTokenEstimator) {
    constructor() : this(PromptTokenEstimator())

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
        val system = IntStream.range(0, messages.size).filter { messages[it] is SystemMessage }
            .map { costs[it] }.sum()
        val available = target.options.contextWindow - reservedOutputTokens - system
        val last = messages.indexOfLast { it !is SystemMessage }
        val required = system + reservedOutputTokens + if (last < 0) 0 else costs[last]
        if (available < 0 || last >= 0 && costs[last] > available) {
            throw ModelContextLimitException(required, target.options.contextWindow)
        }
        if (last < 0) return messages
        val suffix = IntArray(messages.size + 1)
        IntStream.range(0, messages.size).map { messages.lastIndex - it }.forEach { index ->
            suffix[index] = suffix[index + 1] + if (messages[index] is SystemMessage) 0 else costs[index]
        }
        val first = IntStream.rangeClosed(0, last).filter { suffix[it] <= available }.findFirst().orElse(last)
        return IntStream.range(0, messages.size).filter { it >= first || messages[it] is SystemMessage }
            .mapToObj { messages[it] }.toList()
    }
}
