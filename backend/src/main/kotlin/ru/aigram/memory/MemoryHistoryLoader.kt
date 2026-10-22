package ru.aigram.memory

import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.memory.ChatMemoryState
import ru.aigram.domain.memory.MemoryHistoryPage
import ru.aigram.domain.memory.MemorySource
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.telegram.TelegramGateway

private const val TELEGRAM_PAGE_SIZE = 100
private const val MAX_WINDOW_PAGES = 32

@Component
class MemoryHistoryLoader(
    private val telegram: TelegramGateway,
    private val batches: MemoryBatchBuilder,
    private val exclusions: ContextExclusionStore? = null,
) {
    fun load(
        state: ChatMemoryState,
        before: Long,
        settledBefore: Long,
        recent: Boolean,
        fits: (List<MemorySource>) -> Boolean,
        canContinue: () -> Boolean,
    ): MemoryHistoryPage = page(state, before, settledBefore, recent, fits, canContinue, emptyList(), 0)

    private fun page(
        state: ChatMemoryState,
        before: Long,
        settledBefore: Long,
        recent: Boolean,
        fits: (List<MemorySource>) -> Boolean,
        canContinue: () -> Boolean,
        accumulated: List<ChatMessage>,
        depth: Int,
    ): MemoryHistoryPage {
        if (!canContinue()) {
            throw ReplyCancelledException()
        }
        val raw = telegram.history(state.chatId, TELEGRAM_PAGE_SIZE, before).stream()
            .filter { before == 0L || it.id.toLong() < before }
            .sorted(compareBy { it.id.toLong() }).toList()
        val current = exclusions?.decorate(state.accountId, raw) ?: raw
        val combined = current + accumulated
        val candidates = combined.stream().filter {
            !recent || it.date <= settledBefore &&
                (state.headId == 0L || it.id.toLong() > state.headId) &&
                (state.deltaTargetId == 0L || it.id.toLong() <= state.deltaTargetId)
        }.toList()
        val exhausted = raw.size < TELEGRAM_PAGE_SIZE
        val oldest = raw.firstOrNull()?.id?.toLong()
        val anchor = recent && state.headId != 0L && oldest != null && oldest <= state.headId
        if (exhausted || oldest == null || anchor || depth + 1 >= MAX_WINDOW_PAGES ||
            batches.select(candidates, fits).messages.size < candidates.size
        ) {
            return MemoryHistoryPage(combined, exhausted)
        }
        return page(state, oldest, settledBefore, recent, fits, canContinue, combined, depth + 1)
    }
}
