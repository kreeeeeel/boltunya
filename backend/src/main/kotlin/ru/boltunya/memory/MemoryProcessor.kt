package ru.boltunya.memory

import jakarta.inject.Inject
import ru.boltunya.telegram.TelegramGateway
import java.time.Clock

private const val HISTORY_PAGE_MESSAGES = 100
private const val MESSAGE_SETTLE_SECONDS = 10

class MemoryProcessor @Inject constructor(
    private val telegram: TelegramGateway,
    private val store: MemoryStore,
    private val batches: MemoryBatchBuilder,
    private val summarizer: MemorySummarizer,
    private val clock: Clock,
    private val ownerSummarizer: OwnerMemorySummarizer,
) {
    fun process(state: ChatMemoryState, model: String, recent: Boolean, canCommit: () -> Boolean = { true }): Boolean {
        val previous = store.read(state.chatId)
        val before = if (state.headId == 0L) {
            0
        } else if (recent) {
            state.deltaBeforeId
        } else {
            state.backfillBeforeId
        }
        val fetched = telegram.history(state.chatId, HISTORY_PAGE_MESSAGES, before).sortedBy { it.id.toLong() }
        val page = if (recent) {
            val settledBefore = clock.instant().epochSecond - MESSAGE_SETTLE_SECONDS
            fetched.stream().takeWhile { it.date <= settledBefore }.toList()
        } else {
            fetched
        }
        val candidates = if (recent && state.headId != 0L) {
            page.filter {
                it.id.toLong() > state.headId &&
                    (state.deltaTargetId == 0L || it.id.toLong() <= state.deltaTargetId)
            }
        } else {
            page
        }
        if (recent && page.size != fetched.size && candidates.isEmpty()) return false
        val batch = batches.select(candidates)
        val ownerSources = batch.sources.filter { it.speaker == MemorySpeaker.OWNER && it.text.isNotBlank() }
        val ownerUpdate = if (ownerSources.isEmpty()) {
            null
        } else {
            val owner = store.read("")
            OwnerMemoryUpdate(owner, ownerSummarizer.summarizeOwner(model, owner.facts(), ownerSources))
        }
        val summary = if (batch.sources.any {
                it.text.isNotBlank()
            }
        ) {
            summarizer.summarize(model, previous.facts(), batch.sources)
        } else {
            previous.facts()
        }
        val now = clock.instant().epochSecond
        val next = when {
            state.headId == 0L -> state.copy(
                headId = page.lastOrNull()?.id?.toLong() ?: 0,
                headDate = page.lastOrNull()?.date ?: 0,
                backfillBeforeId = batch.messages.firstOrNull()?.id?.toLong() ?: 0,
                backfillComplete = page.isEmpty(),
                dirty = false,
                requested = false,
            )
            !recent -> state.copy(
                backfillBeforeId = batch.messages.firstOrNull()?.id?.toLong() ?: state.backfillBeforeId,
                backfillComplete = page.isEmpty(),
            )
            else -> delta(state, page.map { it.id.toLong() }, batch, page.lastOrNull()?.date ?: state.headDate)
        }.copy(
            updatedAt = now,
            recentUpdatedAt = if (recent) now else state.recentUpdatedAt,
            processedMessages = state.processedMessages + batch.messages.size,
            ownerMemoryVersion = 1,
        )
        return canCommit() && store.commit(state, previous.documentRevision, summary, next, ownerUpdate)
    }

    private fun delta(
        state: ChatMemoryState,
        pageIds: List<Long>,
        batch: MemoryBatch,
        newestDate: Long,
    ): ChatMemoryState {
        val target = state.deltaTargetId.takeIf { it != 0L } ?: pageIds.lastOrNull() ?: state.headId
        val targetDate = if (state.deltaTargetId == 0L) newestDate else state.deltaTargetDate
        val cursor = batch.messages.firstOrNull()?.id?.toLong()
        val reachedAnchor =
            cursor == null || pageIds.any { it <= state.headId } && cursor == pageIds.firstOrNull { it > state.headId }
        return if (reachedAnchor) {
            state.copy(
                headId = maxOf(state.headId, target),
                headDate = targetDate,
                deltaTargetId = 0,
                deltaTargetDate = 0,
                deltaBeforeId = 0,
                requested = false,
                dirty =
                state.observedId > target,
            )
        } else {
            state.copy(
                deltaTargetId = target,
                deltaTargetDate = targetDate,
                deltaBeforeId = cursor ?: state.headId,
                requested = false,
            )
        }
    }
}
