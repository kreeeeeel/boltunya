package ru.aigram.automation

import ru.aigram.automation.activity.ActivityLog
import ru.aigram.automation.activity.ActivityPhase
import ru.aigram.automation.activity.ActivityStatus
import ru.aigram.automation.activity.ReplyJob
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.diagnostics.DiagnosticSource
import ru.aigram.domain.ActivityDetails
import ru.aigram.domain.ActivityEntry
import ru.aigram.domain.ActivityFeedback
import ru.aigram.domain.ActivityFeedbackUpdate
import ru.aigram.domain.ActivityKind
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.GroupReplyMode
import ru.aigram.domain.PreviewJob
import ru.aigram.domain.ReplyQueueState
import ru.aigram.domain.ReplyResult
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.ActivityDeliveryStartedException
import ru.aigram.exception.impl.ActivityFeedbackUnavailableException
import ru.aigram.exception.impl.ActivityNotFoundException
import ru.aigram.exception.impl.BackgroundInferenceDeferredException
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.MemoryUpdateFailedException
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.exception.impl.ReplyDeliveryFailedException
import ru.aigram.exception.impl.TelegramRateLimitException
import ru.aigram.storage.ActivityFeedbackStore
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

private const val MESSAGE_DEBOUNCE_MS = 800L
private const val QUEUE_TICK_MS = 100L
private const val FAILURE_BACKOFF_SECONDS = 30L
private const val STYLE_HISTORY_MESSAGES = 100
private const val MAX_WORKERS = 4
private const val MAX_PENDING_JOBS = 1000

class ReplyCoordinator(
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val automated: AutomatedReplyStore? = null,
    private val tracing: ReplyTraceScope = ReplyTraceScope(),
    private val diagnostics: DiagnosticLog? = null,
    private val feedback: ActivityFeedbackStore? = null,
    private val delivery: ReplyDeliveryPolicy = ReplyDeliveryPolicy(settings),
    private val exclusions: ContextExclusionStore? = null,
    private val generate: (ChatRule, List<ChatMessage>) -> ReplyResult,
) : AutoCloseable,
    MemoryWorkQueue {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val worker = ThreadPoolExecutor(
        MAX_WORKERS,
        MAX_WORKERS,
        0,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_WORKERS),
        Thread.ofPlatform().name("inference-worker-", 0).factory(),
    )
    private val deliveryLock = ReentrantLock(true)
    private val gate = Any()
    private val pending = linkedMapOf<String, ReplyJob>()
    private val latest = ConcurrentHashMap<String, String>()
    private val drafts = ConcurrentHashMap<String, ReplyResult>()
    private val log = ActivityLog()
    private val typing = TypingIndicator(telegram)
    private val epoch = AtomicLong()
    private val tasks = AssistantTaskFilter()
    private var order = 0L
    private var priorityOrder = 0L
    private val active = linkedMapOf<String, ReplyJob>()
    private val jobOrder = compareBy<ReplyJob> {
        if (it.memory != null && !it.memory.explicit &&
            !it.priority
        ) {
            1
        } else {
            0
        }
    }
        .thenBy { it.order }
    private var queuePaused = false
    private var nextReplyAt = 0L

    @Volatile private var closed = false
    private val ticker = scheduler.scheduleWithFixedDelay(
        ::dispatch,
        QUEUE_TICK_MS,
        QUEUE_TICK_MS,
        TimeUnit.MILLISECONDS,
    )

    fun activities(): List<ActivityEntry> = synchronized(gate) {
        val positions = linkedMapOf<String, Int>()
        pending.values.stream().sorted(jobOrder).forEach { positions[it.id] = positions.size + 1 }
        log.summaries().stream().map { entry ->
            val job = pending[entry.id] ?: active[entry.id]
            entry.copy(
                queuePosition = positions[entry.id] ?: 0,
                priority = job?.priority == true || entry.priority,
                waitUntil = job?.waitUntil ?: 0,
                feedbackRating = feedback?.get(entry.id)?.rating,
            )
        }.toList()
    }

    fun queue(): ReplyQueueState = synchronized(gate) {
        ReplyQueueState(queuePaused, pending.size, active.size, settings.get().maxParallelTasks, active.size)
    }

    fun details(id: String): ActivityDetails = synchronized(gate) {
        val details = log.details(id)
        val queued = pending[id]
        val job = queued ?: active[id]
        val position = if (queued == null) {
            0
        } else {
            pending.values.stream().filter { jobOrder.compare(it, queued) < 0 }.count().toInt() + 1
        }
        details.copy(
            entry = details.entry.copy(
                queuePosition = position,
                priority = job?.priority == true || details.entry.priority,
                waitUntil = job?.waitUntil ?: 0,
                feedbackRating = feedback?.get(id)?.rating,
            ),
            feedback = feedback?.get(id),
        )
    }

    fun saveFeedback(id: String, update: ActivityFeedbackUpdate): ActivityFeedback = synchronized(gate) {
        val details = log.details(id)
        val store = feedback
        if (!details.entry.feedbackAvailable || store == null) throw ActivityFeedbackUnavailableException()
        store.save(id, update)
    }

    fun removeFeedback(id: String) = synchronized(gate) {
        val details = log.details(id)
        val store = feedback
        if (!details.entry.feedbackAvailable || store == null) throw ActivityFeedbackUnavailableException()
        store.remove(id)
    }

    fun pauseQueue(paused: Boolean) = synchronized(gate) { queuePaused = paused }
    fun clearCompleted() = synchronized(gate) {
        log.clearCompleted()
        drafts.clear()
    }

    fun enqueueDraft(chatId: String, model: String, action: () -> ReplyResult): PreviewJob = synchronized(gate) {
        if (closed || pending.size >= MAX_PENDING_JOBS) throw ReplyCancelledException()
        val chat = telegram.chats().stream().filter { it.id == chatId }.findFirst().orElse(null)
        if (chat == null) throw ChatNotFoundException()
        val id = UUID.randomUUID().toString()
        val trace = log.add(
            ActivityEntry(
                id,
                chatId,
                chat.title,
                ActivityStatus.QUEUED.value,
                model,
                Instant.now().epochSecond,
                avatarUrl = chat.avatarUrl,
                kind = ActivityKind.DRAFT,
            ),
            chat.preview,
        )
        pending[id] = ReplyJob(
            id,
            ChatMessage(id, chatId, chat.preview, false, chat.date),
            trace,
            System.nanoTime(),
            0,
            ++order,
            action,
            telegram.status().userId,
        )
        preemptAutomaticMemory()
        PreviewJob(id, ActivityStatus.QUEUED.value)
    }

    fun draft(id: String): PreviewJob = synchronized(gate) {
        val entry = log.details(id).entry
        if (entry.kind != ActivityKind.DRAFT) throw ActivityNotFoundException()
        PreviewJob(id, entry.status, drafts[id], if (entry.status == ActivityStatus.ERROR.value) entry.detail else "")
    }

    fun prioritize(id: String) = synchronized(gate) {
        val job = pending[id] ?: throw ActivityNotFoundException()
        job.order = --priorityOrder
        job.priority = true
        job.trace.prioritize()
    }

    fun cancel(id: String) = synchronized(gate) {
        val job = pending.remove(id) ?: active[id]
        if (job == null) {
            log.details(id)
            return@synchronized
        }
        if (job.delivering) throw ActivityDeliveryStartedException()
        job.memory?.cancelByUser()
        cancelJob(job)
    }

    fun onMessage(message: ChatMessage) = synchronized(gate) {
        if (closed) return@synchronized
        typing.cancel(message.chatId)
        latest[message.chatId] = message.id
        ArrayList(pending.values).stream().filter { it.message.chatId == message.chatId && it.memory == null }.forEach {
            pending.remove(it.id)
            cancelJob(it)
        }
        ArrayList(active.values).stream().filter {
            it.message.chatId == message.chatId && it.memory == null
        }.forEach(::cancelJob)
        val preferences = settings.get()
        val rule = settings.rule(message.chatId)
        if (exclusions?.forInference(telegram.status().userId, listOf(message))?.isEmpty() == true) return@synchronized
        val photo =
            rule.analyzePhotos && message.kind == "photo" &&
                message.media?.mimeType in setOf("image/jpeg", "image/png", "image/webp")
        if (message.deleted || message.outgoing || message.text.isBlank() && !photo || preferences.paused ||
            !rule.enabled || !groupAllowed(message, rule)
        ) {
            return@synchronized
        }
        if (preferences.ignoreAssistantTasks && tasks.ignores(message.text)) {
            return@synchronized
        }
        val chat = telegram.chats().stream().filter { it.id == message.chatId }.findFirst().orElse(null)
            ?: return@synchronized
        val delay = (rule.delaySeconds + preferences.replyDelaySeconds) * 1000L + MESSAGE_DEBOUNCE_MS
        val id = UUID.randomUUID().toString()
        val trace = log.add(
            ActivityEntry(
                id,
                message.chatId,
                chat.title,
                ActivityStatus.QUEUED.value,
                settings.resolve(rule).model,
                Instant.now().epochSecond,
                avatarUrl = chat.avatarUrl,
            ),
            message.text,
        )
        pending[id] = ReplyJob(
            id,
            message,
            trace,
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay),
            System.currentTimeMillis() + delay,
            ++order,
        )
        preemptAutomaticMemory()
    }

    override fun enqueueMemory(task: MemoryQueueTask) = synchronized(gate) {
        if (closed || pending.size >= MAX_PENDING_JOBS) throw ReplyCancelledException()
        val trace = log.add(
            ActivityEntry(
                task.id, task.chatId, task.title, ActivityStatus.QUEUED.value, task.model, Instant.now().epochSecond,
                avatarUrl = task.avatarUrl, kind = ActivityKind.MEMORY, priority = task.explicit,
            ),
            "",
        )
        pending[task.id] = ReplyJob(
            task.id,
            ChatMessage(task.id, task.chatId, "", false, 0),
            trace,
            System.nanoTime(),
            0,
            ++order,
            accountId = task.accountId,
            memory = task,
        )
    }

    override fun cancelMemory(id: String, userInitiated: Boolean) = synchronized(gate) {
        val job = pending[id] ?: active[id] ?: return@synchronized
        if (job.memory == null) return@synchronized
        pending.remove(id)
        if (userInitiated) job.memory.cancelByUser()
        cancelJob(job)
    }

    override fun memoryQueuePosition(id: String): Int = synchronized(gate) {
        val job = pending[id] ?: return@synchronized 0
        pending.values.stream().filter { jobOrder.compare(it, job) < 0 }.count().toInt() + 1
    }

    private fun preemptAutomaticMemory() {
        if (active.size < settings.get().maxParallelTasks) return
        val job = active.values.stream().filter { it.memory != null && !it.memory.explicit && !it.priority }
            .findFirst().orElse(null) ?: return
        job.preempted = true
        cancelJob(job)
    }

    private fun dispatch() = synchronized(gate) {
        if (closed || queuePaused) return@synchronized
        val capacity = settings.get().maxParallelTasks - active.size
        if (capacity <= 0) return@synchronized
        val foregroundPending = pending.values.stream().anyMatch {
            it.memory == null &&
                (it.draft != null || !settings.get().paused)
        }
        val jobs = pending.values.stream().filter { job ->
            System.nanoTime() >= job.readyAt && when {
                job.memory != null -> (job.memory.explicit || job.priority || job.memory.canStart()) &&
                    (job.memory.explicit || job.priority || !foregroundPending)
                job.draft != null -> true
                else -> !settings.get().paused && System.nanoTime() >= nextReplyAt
            }
        }.sorted(jobOrder).limit(capacity.toLong()).toList()
        jobs.stream().forEach { job ->
            pending.remove(job.id)
            active[job.id] = job
            worker.execute { respond(job) }
        }
    }

    private fun respond(job: ReplyJob) {
        if (job.memory != null) {
            respondMemory(job)
            return
        }
        if (job.draft != null) {
            respondDraft(job)
            return
        }
        job.thread = Thread.currentThread()
        val message = job.message
        val rule = settings.rule(message.chatId)
        val resolvedRule = settings.resolve(rule)
        val generationEpoch = epoch.get()
        var indicator: java.util.concurrent.ScheduledFuture<*>? = null
        try {
            if (job.cancelled.get() || !allowed(message, rule)) throw ReplyCancelledException()
            job.trace.start()
            if (settings.get().showTyping) {
                indicator =
                    typing.start(message.chatId) {
                        !job.cancelled.get() && allowed(message, rule) &&
                            settings.get().showTyping
                    }
            }
            tracing.capture(job.trace) {
                val history = telegram.history(
                    message.chatId,
                    if (rule.matchMyStyle) STYLE_HISTORY_MESSAGES else rule.contextMessages,
                )
                val context = if (history.stream().anyMatch { it.id == message.id }) history else history + message
                val globalPrompt = settings.get().globalPrompt
                if (job.cancelled.get()) throw ReplyCancelledException()
                val result = generate(rule, context)
                tracing.phase(ActivityPhase.VALIDATING)
                job.trace.generated(result.text)
                deliveryLock.lockInterruptibly()
                try {
                    val remaining = synchronized(gate) { nextReplyAt - System.nanoTime() }
                    if (remaining > 0) {
                        tracing.phase(ActivityPhase.WAITING_DELIVERY)
                        TimeUnit.NANOSECONDS.sleep(remaining)
                    }
                    synchronized(gate) {
                        if (job.cancelled.get() || !allowed(message, rule) || settings.rule(message.chatId) != rule ||
                            settings.resolve(settings.rule(message.chatId)) != resolvedRule ||
                            settings.get().globalPrompt != globalPrompt || epoch.get() != generationEpoch
                        ) {
                            throw ReplyCancelledException()
                        }
                        tracing.phase(ActivityPhase.SENDING)
                        job.delivering = true
                    }
                    if (result.text.isNotBlank()) {
                        val sentId = telegram.send(
                            message.chatId,
                            result.text,
                            delivery.replyTo(rule, message.id),
                            message.topicId,
                        )
                        synchronized(gate) {
                            nextReplyAt =
                                System.nanoTime() +
                                TimeUnit.SECONDS.toNanos(settings.get().replyIntervalSeconds.toLong())
                        }
                        automated?.record(message.chatId, sentId)
                    }
                    if (result.stickerId.isNotEmpty()) {
                        val sentId = telegram.sendSticker(
                            message.chatId,
                            result.stickerId,
                            delivery.replyTo(rule, message.id),
                            message.topicId,
                        )
                        automated?.record(message.chatId, sentId)
                        synchronized(gate) {
                            nextReplyAt =
                                System.nanoTime() +
                                TimeUnit.SECONDS.toNanos(settings.get().replyIntervalSeconds.toLong())
                        }
                    }
                } catch (exception: Exception) {
                    if (job.delivering && exception !is ReplyCancelledException &&
                        !job.cancelled.get() && !Thread.currentThread().isInterrupted
                    ) {
                        val seconds = if (exception is TelegramRateLimitException) {
                            exception.retryAfterSeconds
                        } else {
                            FAILURE_BACKOFF_SECONDS
                        }
                        synchronized(gate) { nextReplyAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds) }
                    }
                    throw exception
                } finally {
                    deliveryLock.unlock()
                }
                job.trace.finish(ActivityStatus.SENT, result.model.ifBlank { resolvedRule.model }, result.text)
            }
        } catch (exception: Exception) {
            if (job.cancelled.get() || exception is ReplyCancelledException || Thread.currentThread().isInterrupted) {
                job.trace.finish(ActivityStatus.CANCELLED, ReplyCancelledException().message)
            } else {
                diagnostics?.record(DiagnosticSource.REPLY, exception)
                job.trace.finish(
                    ActivityStatus.ERROR,
                    if (exception is AppException) exception.message else ReplyDeliveryFailedException().message,
                )
                System.err.println("Auto-reply failed: ${exception.javaClass.simpleName}")
            }
        } finally {
            Thread.interrupted()
            typing.stop(message.chatId, indicator)
            job.thread = null
            synchronized(gate) { active.remove(job.id) }
            log.prune()
        }
    }

    private fun respondDraft(job: ReplyJob) {
        job.thread = Thread.currentThread()
        try {
            if (job.cancelled.get() || closed ||
                telegram.status().userId != job.accountId
            ) {
                throw ReplyCancelledException()
            }
            job.trace.start()
            val generate = job.draft
            if (generate == null) throw ReplyCancelledException()
            tracing.capture(job.trace) {
                val result = generate()
                synchronized(gate) {
                    if (job.cancelled.get() || closed ||
                        telegram.status().userId != job.accountId
                    ) {
                        throw ReplyCancelledException()
                    }
                    drafts[job.id] = result
                    job.trace.generated(result.text)
                    job.trace.finish(ActivityStatus.READY, result.model, result.text)
                }
            }
        } catch (exception: Exception) {
            if (job.cancelled.get() || exception is ReplyCancelledException || Thread.currentThread().isInterrupted) {
                job.trace.finish(ActivityStatus.CANCELLED, ReplyCancelledException().message)
            } else {
                diagnostics?.record(DiagnosticSource.REPLY, exception)
                job.trace.finish(
                    ActivityStatus.ERROR,
                    if (exception is AppException) exception.message else ReplyDeliveryFailedException().message,
                )
            }
        } finally {
            Thread.interrupted()
            job.thread = null
            synchronized(gate) { active.remove(job.id) }
            log.prune()
            val retained = log.summaries().stream().map { it.id }.toList().toSet()
            drafts.keys.retainAll(retained)
        }
    }

    private fun respondMemory(job: ReplyJob) {
        job.thread = Thread.currentThread()
        var failure: Throwable? = null
        try {
            if (job.cancelled.get() || closed ||
                telegram.status().userId != job.accountId
            ) {
                throw ReplyCancelledException()
            }
            job.trace.start()
            job.trace.phase(ActivityPhase.MEMORY)
            val task = job.memory
            if (task == null) throw ReplyCancelledException()
            tracing.capture(job.trace) { task.action() }
            if (job.cancelled.get()) throw ReplyCancelledException()
            job.trace.finish(ActivityStatus.READY, task.model)
        } catch (exception: Exception) {
            val cancelled = job.cancelled.get() || exception is ReplyCancelledException ||
                exception is BackgroundInferenceDeferredException || Thread.currentThread().isInterrupted
            failure = when {
                job.preempted || exception is BackgroundInferenceDeferredException ->
                    BackgroundInferenceDeferredException()
                cancelled -> ReplyCancelledException()
                else -> exception
            }
            if (cancelled) {
                job.trace.finish(
                    ActivityStatus.CANCELLED,
                    if (failure is AppException) failure.message else ReplyCancelledException().message,
                )
            } else {
                diagnostics?.record(DiagnosticSource.MEMORY, exception)
                job.trace.finish(
                    ActivityStatus.ERROR,
                    if (exception is AppException) exception.message else MemoryUpdateFailedException().message,
                )
            }
        } finally {
            Thread.interrupted()
            job.thread = null
            synchronized(gate) { active.remove(job.id) }
            job.memory?.finish(failure)
            log.prune()
        }
    }

    private fun cancelJob(job: ReplyJob) {
        if (job.delivering || !job.trace.active()) return
        job.cancel()
        job.trace.finish(ActivityStatus.CANCELLED, ReplyCancelledException().message)
        if (job.memory == null) typing.cancel(job.message.chatId)
        if (job.thread ==
            null
        ) {
            job.memory?.finish(
                if (job.preempted) BackgroundInferenceDeferredException() else ReplyCancelledException(),
            )
        }
    }

    private fun allowed(message: ChatMessage, rule: ChatRule): Boolean =
        !closed && !settings.get().paused && rule.enabled &&
            latest[message.chatId] == message.id && groupAllowed(message, rule)

    private fun groupAllowed(message: ChatMessage, rule: ChatRule): Boolean {
        if (!message.group) return true
        val mode = if (rule.groupReplies == GroupReplyMode.INHERIT) settings.get().groupReplies else rule.groupReplies
        return mode == GroupReplyMode.ALL || mode == GroupReplyMode.MENTIONS && message.mentionsMe
    }

    fun cancelPending() = synchronized(gate) {
        epoch.incrementAndGet()
        ArrayList(pending.values).stream().forEach(::cancelJob)
        pending.clear()
        ArrayList(active.values).stream().forEach(::cancelJob)
        typing.cancelAll()
    }

    fun invalidateContext(chatId: String) = synchronized(gate) {
        ArrayList(pending.values).stream().filter { it.message.chatId == chatId }.forEach {
            pending.remove(it.id)
            cancelJob(it)
        }
        ArrayList(active.values).stream().filter { it.message.chatId == chatId && !it.delivering }.forEach(::cancelJob)
        typing.cancel(chatId)
    }

    fun onDeleted(chatId: String, ids: Set<String>) = synchronized(gate) {
        if (latest[chatId] !in ids) return@synchronized
        latest.remove(chatId)
        ArrayList(pending.values).stream().filter { it.message.chatId == chatId }.forEach {
            pending.remove(it.id)
            cancelJob(it)
        }
        ArrayList(active.values).stream().filter { it.message.chatId == chatId }.forEach(::cancelJob)
        typing.cancel(chatId)
    }

    override fun close() {
        synchronized(gate) {
            closed = true
            cancelPending()
        }
        ticker.cancel(false)
        scheduler.shutdownNow()
        worker.shutdownNow()
        typing.close()
    }
}
