package ru.aigram.memory

import org.springframework.stereotype.Component
import ru.aigram.domain.memory.ChatMemoryState
import ru.aigram.domain.memory.MemoryBatch
import ru.aigram.domain.memory.MemorySource
import ru.aigram.domain.memory.MemorySpeaker
import ru.aigram.domain.memory.OwnerMemoryUpdate
import ru.aigram.speech.reply.SpeechInputContext
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.telegram.TelegramGateway
import java.time.Clock

private const val HISTORY_PAGE_MESSAGES = 100
private const val MESSAGE_SETTLE_SECONDS = 10

@Component
class MemoryProcessor(
    private val telegram: TelegramGateway,
    private val store: MemoryStore,
    private val batches: MemoryBatchBuilder,
    private val summarizer: MemorySummarizer,
    private val clock: Clock,
    private val ownerSummarizer: OwnerMemorySummarizer,
    private val exclusions: ContextExclusionStore? = null,
    private val roster: ParticipantRosterStore? = null,
    private val speech: SpeechInputContext? = null,
) {
    fun process(state: ChatMemoryState, model: String, recent: Boolean, canCommit: () -> Boolean = { true }): Boolean {
        val policyRevision = exclusions?.revision(state.accountId, state.chatId)
        val previous = store.read(state.chatId)
        val before = if (state.headId == 0L) {
            0
        } else if (recent) {
            state.deltaBeforeId
        } else {
            state.backfillBeforeId
        }
        val history = telegram.history(state.chatId, HISTORY_PAGE_MESSAGES, before).sortedBy { it.id.toLong() }
        val fetched = exclusions?.decorate(state.accountId, history) ?: history
        if (unchanged(state, policyRevision)) {
            roster?.record(state.accountId, state.chatId, fetched)
        }
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
        if (recent && page.size != fetched.size && candidates.isEmpty()) {
            return false
        }
        val owner = store.read("")
        val fits: (List<MemorySource>) -> Boolean = { sources ->
            val owned = sources.filter { it.speaker == MemorySpeaker.OWNER && it.text.isNotBlank() }
            summarizer.fits(model, previous.facts(), sources) &&
                (owned.isEmpty() || ownerSummarizer.fitsOwner(model, owner.facts(), owned))
        }
        val selected = batches.select(candidates, fits)
        val prepared = speech?.prepare(selected.messages, rejectDisabledVoice = false, ownerId = state.accountId)
            ?: selected.messages
        val batch = batches.select(prepared, fits)
        if (!unchanged(state, policyRevision)) {
            return false
        }
        val ownerSources = batch.sources.filter { it.speaker == MemorySpeaker.OWNER && it.text.isNotBlank() }
        val ownerUpdate = if (ownerSources.isEmpty()) {
            null
        } else {
            OwnerMemoryUpdate(owner, ownerSummarizer.summarizeOwner(model, owner.facts(), ownerSources))
        }
        if (!unchanged(state, policyRevision)) {
            return false
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
            recentUpdatedAt = if (recent) {
                now
            } else {
                state.recentUpdatedAt
            },
            processedMessages = state.processedMessages + batch.messages.size,
            ownerMemoryVersion = 1,
        )
        val policy = exclusions
        return if (policy == null) {
            canCommit() && store.commit(state, previous.documentRevision, summary, next, ownerUpdate)
        } else {
            synchronized(policy) {
                unchanged(state, policyRevision) && canCommit() &&
                    store.commit(state, previous.documentRevision, summary, next, ownerUpdate)
            }
        }
    }

    private fun unchanged(state: ChatMemoryState, revision: Long?): Boolean =
        exclusions == null || exclusions.revision(state.accountId, state.chatId) == revision

    private fun delta(
        state: ChatMemoryState,
        pageIds: List<Long>,
        batch: MemoryBatch,
        newestDate: Long,
    ): ChatMemoryState {
        val target = state.deltaTargetId.takeIf { it != 0L } ?: pageIds.lastOrNull() ?: state.headId
        val targetDate = if (state.deltaTargetId == 0L) {
            newestDate
        } else {
            state.deltaTargetDate
        }
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
                dirty = state.observedId > target,
            )
        } else {
            state.copy(
                deltaTargetId = target,
                deltaTargetDate = targetDate,
                deltaBeforeId = cursor,
                requested = false,
            )
        }
    }
}
