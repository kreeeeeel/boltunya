package ru.boltunya.ai.profile

import jakarta.inject.Inject
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import ru.boltunya.exception.impl.ModelContextLimitException
import java.util.stream.IntStream

private const val MESSAGE_OVERHEAD_TOKENS = 16

/** A conservative text budget, not a claim to reproduce a remote provider's tokenizer or image accounting. */
class ProfileContextBudget @Inject constructor() {
    fun select(target: ResolvedModelTarget, messages: List<Message>, outputTokens: Int): List<Message> {
        if (target.provider == ModelProvider.OLLAMA || messages.isEmpty()) return messages
        val reserve = minOf(
            target.options.maxOutputTokens,
            outputTokens + if (target.capabilities.reasoning) target.options.reasoningTokens else 0,
        )
        val system = messages.stream().filter { it is SystemMessage }.mapToInt(::estimate).sum()
        val available = target.options.contextWindow - reserve - system
        val first = IntStream.range(0, messages.size).filter { index ->
            messages.subList(index, messages.size).stream().filter {
                it !is SystemMessage
            }.mapToInt(::estimate).sum() <=
                available
        }.findFirst().orElse(-1)
        if (first < 0) throw ModelContextLimitException()
        return IntStream.range(0, messages.size).filter { it >= first || messages[it] is SystemMessage }
            .mapToObj { messages[it] }.toList()
    }

    private fun estimate(message: Message): Int =
        message.text.orEmpty().toByteArray(Charsets.UTF_8).size + MESSAGE_OVERHEAD_TOKENS
}
