package ru.aigram.automation

import ru.aigram.automation.activity.ActivityHistoryStore
import ru.aigram.automation.activity.ActivityLog
import ru.aigram.automation.activity.ReplyJob
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.automation.activity.withCurrentAvatar
import ru.aigram.automation.proactive.ProactiveTask
import ru.aigram.domain.ai.PreviewJob
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.domain.chat.ProactiveMode
import ru.aigram.domain.chat.ReplyInterruptionMode
import ru.aigram.domain.events.ActivityDetails
import ru.aigram.domain.events.ActivityEntry
import ru.aigram.domain.events.ActivityFeedback
import ru.aigram.domain.events.ActivityFeedbackUpdate
import ru.aigram.domain.events.ActivityHistoryPage
import ru.aigram.domain.events.ActivityKind
import ru.aigram.domain.events.ActivityPhase
import ru.aigram.domain.events.ActivityStatus
import ru.aigram.domain.events.ReplyQueueState
import ru.aigram.domain.settings.AppSettings
import ru.aigram.domain.settings.ReplyProcessingMode
import ru.aigram.exception.AppException
import ru.aigram.exception.impl.ActivityDeliveryStartedException
import ru.aigram.exception.impl.ActivityFeedbackUnavailableException
import ru.aigram.exception.impl.ActivityNotFoundException
import ru.aigram.exception.impl.BackgroundInferenceDeferredException
import ru.aigram.exception.impl.ChatNotFoundException
import ru.aigram.exception.impl.MemoryUpdateFailedException
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.exception.impl.ReplyDeliveryFailedException
import ru.aigram.exception.impl.SpeechDraftUnavailableException
import ru.aigram.exception.impl.TelegramRateLimitException
import ru.aigram.speech.reply.VoiceDraftStore
import ru.aigram.speech.reply.voicePreferences
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
import java.util.stream.Collectors

private const val MESSAGE_DEBOUNCE_MS = 800L
private const val QUEUE_TICK_MS = 100L
private const val FAILURE_BACKOFF_SECONDS = 30L
private const val STYLE_HISTORY_MESSAGES = 100
private const val MIN_VISION_HISTORY_MESSAGES = 20
private const val MAX_WORKERS = 4
private const val MAX_PENDING_JOBS = 1000

class ReplyCoordinator(
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val automated: AutomatedReplyStore? = null,
    private val tracing: ReplyTraceScope = ReplyTraceScope(),
    private val feedback: ActivityFeedbackStore? = null,
    private val delivery: ReplyDeliveryPolicy = ReplyDeliveryPolicy(settings),
    private val exclusions: ContextExclusionStore? = null,
    private val history: ActivityHistoryStore? = null,
    private val voices: VoiceDraftStore? = null,
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
    private val log = ActivityLog(history) { telegram.status().userId }
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
        val chats = currentChats()
        pending.values.stream().sorted(jobOrder).forEach { positions[it.id] = positions.size + 1 }
        log.summaries().stream().map { entry ->
            val job = pending[entry.id] ?: active[entry.id]
            entry.copy(
                queuePosition = positions[entry.id] ?: 0,
                priority = job?.priority == true || entry.priority,
                waitUntil = job?.waitUntil ?: 0,
                feedbackRating = feedback?.get(entry.id)?.rating,
            ).withCurrentAvatar(chats[entry.chatId])
        }.toList()
    }

    fun currentActivities(): List<ActivityEntry> {
        var completed = 0
        return activities().stream().filter { entry ->
            entry.status == "queued" || entry.status == "generating" || ++completed <= 30
        }.toList()
    }

    fun historyPage(cursor: String?, limit: Int): ActivityHistoryPage {
        val page = history?.page(telegram.status().userId, cursor, limit)

        if (page == null) {
            return ActivityHistoryPage()
        }

        val chats = currentChats()
        return page.copy(
            entries = page.entries.stream().map { entry ->
                entry.copy(feedbackRating = feedback?.get(entry.id)?.rating).withCurrentAvatar(chats[entry.chatId])
            }.toList(),
        )
    }

    fun queue(): ReplyQueueState = synchronized(gate) {
        ReplyQueueState(queuePaused, pending.size, active.size, settings.get().maxParallelTasks, active.size)
    }

    fun details(id: String): ActivityDetails {
        val ownerId = telegram.status().userId
        val details = log.details(id, ownerId)
        val chats = currentChats()

        return synchronized(gate) {
            if (telegram.status().userId != ownerId) {
                throw ActivityNotFoundException()
            }

            val currentFeedback = feedback?.get(id)
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
                    feedbackRating = currentFeedback?.rating,
                ).withCurrentAvatar(chats[details.entry.chatId]),
                feedback = currentFeedback,
            )
        }
    }

    private fun currentChats(): Map<String, ChatSummary> = telegram.chats().stream()
        .collect(Collectors.toMap(ChatSummary::id) { it })

    fun saveFeedback(id: String, update: ActivityFeedbackUpdate): ActivityFeedback = synchronized(gate) {
        val details = log.details(id)
        val store = feedback
        if (!details.entry.feedbackAvailable || store == null) {
            throw ActivityFeedbackUnavailableException()
        }
        store.save(id, update)
    }

    fun removeFeedback(id: String) = synchronized(gate) {
        val details = log.details(id)
        val store = feedback
        if (!details.entry.feedbackAvailable || store == null) {
            throw ActivityFeedbackUnavailableException()
        }
        store.remove(id)
    }

    fun pauseQueue(paused: Boolean) = synchronized(gate) { queuePaused = paused }
    fun clearCompleted() = synchronized(gate) {
        log.clearCompleted()
        drafts.clear()
    }

    fun enqueueDraft(chatId: String, model: String, action: () -> ReplyResult): PreviewJob = synchronized(gate) {
        if (closed) {
            throw ReplyCancelledException()
        }

        val chat = telegram.chats().stream().filter { it.id == chatId }.findFirst().orElse(null)
        if (chat == null) {
            throw ChatNotFoundException()
        }
        enqueueDraft(chat, model, action)
    }

    fun enqueueToolDraft(title: String, model: String, trigger: String, action: () -> ReplyResult): PreviewJob =
        synchronized(gate) {
            enqueueDraft(ChatSummary("", title, trigger, 0, 0, ChatRule()), model, action)
        }

    private fun enqueueDraft(chat: ChatSummary, model: String, action: () -> ReplyResult): PreviewJob {
        if (closed || pending.size >= MAX_PENDING_JOBS) {
            throw ReplyCancelledException()
        }
        val id = UUID.randomUUID().toString()
        val trace = log.add(
            ActivityEntry(
                id,
                chat.id,
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
            ChatMessage(id, chat.id, chat.preview, false, chat.date),
            trace,
            System.nanoTime(),
            0,
            ++order,
            action,
            telegram.status().userId,
        )
        return PreviewJob(id, ActivityStatus.QUEUED.value)
    }

    fun hasWork(chatId: String): Boolean = synchronized(gate) {
        pending.values.any { it.message.chatId == chatId } || active.values.any { it.message.chatId == chatId }
    }

    fun hasInitiative(): Boolean = synchronized(gate) {
        pending.values.any { it.proactive != null } || active.values.any { it.proactive != null }
    }

    fun enqueueInitiative(chat: ChatSummary, accountId: String, task: ProactiveTask): Boolean = synchronized(gate) {
        if (closed || queuePaused || settings.get().paused || pending.size >= MAX_PENDING_JOBS ||
            hasWork(chat.id) || !task.allowed()
        ) {
            return@synchronized false
        }
        val id = UUID.randomUUID().toString()
        val trace = log.add(
            ActivityEntry(
                id,
                chat.id,
                chat.title,
                ActivityStatus.QUEUED.value,
                settings.rule(chat.id).model,
                Instant.now().epochSecond,
                avatarUrl = chat.avatarUrl,
                kind = ActivityKind.INITIATIVE,
            ),
            task.trigger,
        )
        latest[chat.id] = chat.lastMessageId
        val delay = TimeUnit.SECONDS.toNanos(
            (settings.get().replyDelaySeconds + settings.rule(chat.id).delaySeconds).toLong(),
        )
        pending[id] = ReplyJob(
            id,
            ChatMessage(
                chat.lastMessageId,
                chat.id,
                "",
                true,
                chat.date,
                group = chat.kind != "private",
                mentionsMe = true,
            ),
            trace,
            System.nanoTime() + delay,
            System.currentTimeMillis() + TimeUnit.NANOSECONDS.toMillis(delay),
            ++order,
            accountId = accountId,
            proactive = task,
        )
        true
    }

    fun draft(id: String): PreviewJob = synchronized(gate) {
        val entry = log.details(id).entry
        if (entry.kind != ActivityKind.DRAFT) {
            throw ActivityNotFoundException()
        }
        PreviewJob(
            id,
            entry.status,
            drafts[id],
            if (entry.status == ActivityStatus.ERROR.value) {
                entry.detail
            } else {
                ""
            },
        )
    }

    fun prioritize(id: String) = synchronized(gate) {
        val job = pending[id]

        if (job == null) {
            throw ActivityNotFoundException()
        }

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
        if (job.delivering) {
            throw ActivityDeliveryStartedException()
        }
        job.memory?.cancelByUser()
        cancelJob(job)
    }

    fun onMessage(message: ChatMessage) = synchronized(gate) {
        if (closed) {
            return@synchronized
        }
        if (message.outgoing && message.automated) {
            return@synchronized
        }
        val preferences = settings.get()
        val rule = settings.rule(message.chatId)
        val account = telegram.status().userId
        val mode = processingMode(rule)
        val finishCurrent = !message.outgoing && !message.deleted && !preferences.paused && rule.enabled &&
            mode != ReplyProcessingMode.REPLACE
        val photo = rule.analyzePhotos && message.kind == "photo" &&
            message.media?.mimeType in setOf("image/jpeg", "image/png", "image/webp")
        val suitable = !message.deleted && !message.outgoing && (message.text.isNotBlank() || photo) &&
            (message.kind != "voice" || preferences.voicePreferences(rule).inputEnabled) &&
            !preferences.paused && rule.enabled && groupAllowed(message, rule) &&
            exclusions?.forInference(account, listOf(message))?.isEmpty() != true &&
            (!preferences.ignoreAssistantTasks || !tasks.ignores(message.text))
        val chat = if (suitable) {
            telegram.chats().stream().filter { it.id == message.chatId }.findFirst().orElse(null)
        } else {
            null
        }
        val retainPending = finishCurrent && (mode == ReplyProcessingMode.EACH || chat == null)
        latest[message.chatId] = message.id
        ArrayList(pending.values).stream().filter { it.message.chatId == message.chatId && it.memory == null }
            .filter {
                !ordinaryReply(it) || !retainPending ||
                    it.processingMode != mode || it.accountId != account
            }
            .forEach {
                pending.remove(it.id)
                cancelJob(it)
            }
        ArrayList(active.values).stream().filter {
            it.message.chatId == message.chatId && it.memory == null
        }.filter { job ->
            !ordinaryReply(job) || !finishCurrent || job.processingMode != mode || job.accountId != account
        }.forEach(::cancelJob)
        val retainedActive = active.values.stream().anyMatch {
            it.message.chatId == message.chatId && ordinaryReply(it) && !it.cancelled.get()
        }
        if (!retainedActive) {
            typing.cancel(message.chatId)
        }

        if (chat == null) {
            return@synchronized
        }
        if (pending.size >= MAX_PENDING_JOBS) {
            throw ReplyCancelledException()
        }

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
            accountId = account,
            processingMode = mode,
        )
    }

    override fun enqueueMemory(task: MemoryQueueTask) = synchronized(gate) {
        if (closed || pending.size >= MAX_PENDING_JOBS) {
            throw ReplyCancelledException()
        }
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
        val job = pending[id] ?: active[id]

        if (job == null) {
            return@synchronized
        }

        if (job.memory == null) {
            return@synchronized
        }
        pending.remove(id)
        if (userInitiated) {
            job.memory.cancelByUser()
        }
        cancelJob(job)
    }

    override fun memoryQueuePosition(id: String): Int = synchronized(gate) {
        val job = pending[id]

        if (job == null) {
            return@synchronized 0
        }

        pending.values.stream().filter { jobOrder.compare(it, job) < 0 }.count().toInt() + 1
    }

    private fun dispatch() = synchronized(gate) {
        if (closed || queuePaused) {
            return@synchronized
        }
        val capacity = settings.get().maxParallelTasks - active.size
        if (capacity <= 0) {
            return@synchronized
        }
        val foregroundPending = pending.values.stream().anyMatch {
            it.memory == null &&
                (it.draft != null || !settings.get().paused)
        }
        val firstReplies = pending.values.stream().filter(::ordinaryReply).collect(
            Collectors.toMap({ it.message.chatId }, { it }, { first, second ->
                if (jobOrder.compare(first, second) <= 0) {
                    first
                } else {
                    second
                }
            }),
        )
        val jobs = pending.values.stream().filter { job ->
            (
                !ordinaryReply(job) || firstReplies[job.message.chatId] === job && active.values.stream().noneMatch {
                    ordinaryReply(it) && it.message.chatId == job.message.chatId
                }
                ) && System.nanoTime() >= job.readyAt && when {
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
        val voicePreferences = settings.get().voicePreferences(rule)
        val generationEpoch = epoch.get()
        fun canReply(): Boolean = telegram.status().userId == job.accountId &&
            settings.get().voicePreferences(settings.rule(message.chatId)) == voicePreferences &&
            (!ordinaryReply(job) || job.processingMode == processingMode(rule)) &&
            allowed(
                message,
                rule,
                job.proactive != null,
                ordinaryReply(job) && job.processingMode != ReplyProcessingMode.REPLACE,
            ) &&
            (job.proactive?.allowed?.invoke() ?: true)
        var indicator: java.util.concurrent.ScheduledFuture<*>? = null
        try {
            if (job.cancelled.get() || !canReply()) {
                throw ReplyCancelledException()
            }
            job.trace.start()
            if (settings.get().showTyping) {
                indicator =
                    typing.start(message.chatId) {
                        !job.cancelled.get() && canReply() &&
                            settings.get().showTyping
                    }
            }
            tracing.capture(job.trace) {
                val anchorId = message.id.toLongOrNull()
                val before = if (job.processingMode != ReplyProcessingMode.REPLACE && anchorId != null &&
                    anchorId > 0 && anchorId < Long.MAX_VALUE
                ) {
                    anchorId + 1
                } else {
                    0
                }
                val history = telegram.history(
                    message.chatId,
                    maxOf(
                        if (rule.matchMyStyle) {
                            STYLE_HISTORY_MESSAGES
                        } else {
                            rule.contextMessages
                        },
                        if (rule.analyzePhotos) {
                            MIN_VISION_HISTORY_MESSAGES
                        } else {
                            rule.contextMessages
                        },
                    ),
                    before,
                )
                val context = if (ordinaryReply(job) && job.processingMode != ReplyProcessingMode.REPLACE) {
                    val bounded = history.stream().filter { item ->
                        if (anchorId != null && anchorId > 0) {
                            val id = item.id.toLongOrNull()
                            id != null && id > 0 && id <= anchorId
                        } else {
                            item.date < message.date || item.id == message.id
                        }
                    }.toList()
                    val anchor = bounded.stream().filter { it.id == message.id }.findFirst().orElse(message)
                    bounded.stream().filter { it.id != message.id }.toList() + anchor
                } else if (job.proactive != null || history.stream().anyMatch { it.id == message.id }) {
                    history
                } else {
                    history + message
                }
                val topicId = if (job.proactive != null) {
                    context.lastOrNull { it.id == message.id }?.topicId ?: message.topicId
                } else {
                    message.topicId
                }
                val globalPrompt = settings.get().globalPrompt
                if (job.cancelled.get()) {
                    throw ReplyCancelledException()
                }
                val result = job.proactive?.generate?.invoke(rule, context) ?: generate(rule, context)
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
                        if (job.cancelled.get() || !canReply() || settings.rule(message.chatId) != rule ||
                            settings.resolve(settings.rule(message.chatId)) != resolvedRule ||
                            settings.get().globalPrompt != globalPrompt || epoch.get() != generationEpoch
                        ) {
                            throw ReplyCancelledException()
                        }
                        tracing.phase(ActivityPhase.SENDING)
                        job.delivering = true
                    }
                    if ((result.text.isNotBlank() || result.stickerId.isNotEmpty()) &&
                        job.proactive?.prepareDelivery?.invoke() == false
                    ) {
                        throw ReplyCancelledException()
                    }
                    if (result.voice != null) {
                        val store = voices
                        if (store == null) {
                            throw SpeechDraftUnavailableException()
                        }
                        val sentId = store.send(
                            message.chatId,
                            result.voice.id,
                            delivery.replyTo(rule, message.id),
                            topicId,
                            automated = true,
                        )
                        automated?.record(message.chatId, sentId)
                        synchronized(gate) {
                            nextReplyAt =
                                System.nanoTime() +
                                TimeUnit.SECONDS.toNanos(settings.get().replyIntervalSeconds.toLong())
                        }
                    } else if (result.text.isNotBlank()) {
                        val sentId = telegram.send(
                            message.chatId,
                            result.text,
                            delivery.replyTo(rule, message.id),
                            topicId,
                            result.entities,
                            automated = true,
                        )
                        synchronized(gate) {
                            nextReplyAt =
                                System.nanoTime() +
                                TimeUnit.SECONDS.toNanos(settings.get().replyIntervalSeconds.toLong())
                        }
                        automated?.record(message.chatId, sentId)
                    }
                    if (result.stickerId.isNotEmpty() && result.voice == null) {
                        val sentId = telegram.sendSticker(
                            message.chatId,
                            result.stickerId,
                            delivery.replyTo(rule, message.id),
                            topicId,
                            automated = true,
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
                job.trace.finish(
                    ActivityStatus.ERROR,
                    if (exception is AppException) {
                        exception.message
                    } else {
                        ReplyDeliveryFailedException().message
                    },
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
            if (generate == null) {
                throw ReplyCancelledException()
            }
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
                job.trace.finish(
                    ActivityStatus.ERROR,
                    if (exception is AppException) {
                        exception.message
                    } else {
                        ReplyDeliveryFailedException().message
                    },
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
        var deferred = false
        try {
            if (job.cancelled.get() || closed ||
                telegram.status().userId != job.accountId
            ) {
                throw ReplyCancelledException()
            }
            job.trace.start()
            job.trace.phase(ActivityPhase.MEMORY)
            val task = job.memory
            if (task == null) {
                throw ReplyCancelledException()
            }
            tracing.capture(job.trace) { task.action() }
            if (job.cancelled.get()) {
                throw ReplyCancelledException()
            }
            job.trace.finish(ActivityStatus.READY, task.model)
        } catch (exception: Exception) {
            deferred = exception is BackgroundInferenceDeferredException && !job.cancelled.get() && !closed
            val cancelled = job.cancelled.get() || exception is ReplyCancelledException ||
                Thread.currentThread().isInterrupted
            failure = when {
                exception is BackgroundInferenceDeferredException -> exception
                cancelled -> ReplyCancelledException()
                else -> exception
            }
            if (deferred) {
                job.trace.defer()
            } else if (cancelled) {
                job.trace.finish(
                    ActivityStatus.CANCELLED,
                    if (failure is AppException) {
                        failure.message
                    } else {
                        ReplyCancelledException().message
                    },
                )
            } else {
                job.trace.finish(
                    ActivityStatus.ERROR,
                    if (exception is AppException) {
                        exception.message
                    } else {
                        MemoryUpdateFailedException().message
                    },
                )
            }
        } finally {
            Thread.interrupted()
            job.thread = null
            synchronized(gate) {
                active.remove(job.id)
                if (deferred && !closed && !job.cancelled.get()) {
                    val retryDelay = (failure as? BackgroundInferenceDeferredException)?.retryDelayMillis
                        ?: QUEUE_TICK_MS
                    job.readyAt =
                        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(retryDelay.coerceAtLeast(QUEUE_TICK_MS))
                    pending[job.id] = job
                } else {
                    deferred = false
                }
            }
            if (!deferred) {
                job.memory?.finish(failure)
            }
            log.prune()
        }
    }

    private fun cancelJob(job: ReplyJob) {
        if (job.delivering || !job.trace.active()) {
            return
        }
        job.cancel()
        job.trace.finish(ActivityStatus.CANCELLED, ReplyCancelledException().message)
        if (job.memory == null && job.thread != null) {
            typing.cancel(job.message.chatId)
        }
        if (job.thread ==
            null
        ) {
            job.memory?.finish(
                ReplyCancelledException(),
            )
        }
    }

    private fun allowed(
        message: ChatMessage,
        rule: ChatRule,
        initiative: Boolean = false,
        finishCurrent: Boolean = false,
    ): Boolean = !closed && !settings.get().paused &&
        (rule.enabled || initiative && rule.proactiveMode == ProactiveMode.ENABLED) &&
        (finishCurrent || latest[message.chatId] == message.id) && groupAllowed(message, rule)

    private fun ordinaryReply(job: ReplyJob): Boolean = job.memory == null && job.draft == null && job.proactive == null

    private fun processingMode(rule: ChatRule): ReplyProcessingMode = when (rule.replyInterruptionMode) {
        ReplyInterruptionMode.INHERIT -> settings.get().replyProcessingMode
        ReplyInterruptionMode.REPLACE -> ReplyProcessingMode.REPLACE
        ReplyInterruptionMode.FINISH -> ReplyProcessingMode.FINISH
        ReplyInterruptionMode.EACH -> ReplyProcessingMode.EACH
    }

    private fun groupAllowed(message: ChatMessage, rule: ChatRule): Boolean {
        if (!message.group) {
            return true
        }
        val mode = if (rule.groupReplies == GroupReplyMode.INHERIT) {
            settings.get().groupReplies
        } else {
            rule.groupReplies
        }
        return mode == GroupReplyMode.ALL || mode == GroupReplyMode.MENTIONS && message.mentionsMe
    }

    fun cancelReplies(chatId: String? = null) = synchronized(gate) {
        if (chatId == null) {
            epoch.incrementAndGet()
        }
        ArrayList(pending.values).stream().filter {
            it.memory == null && (chatId == null || it.message.chatId == chatId)
        }.forEach {
            pending.remove(it.id)
            cancelJob(it)
        }
        ArrayList(active.values).stream().filter {
            it.memory == null && (chatId == null || it.message.chatId == chatId)
        }.forEach(::cancelJob)
        if (chatId == null) {
            typing.cancelAll()
        } else {
            typing.cancel(chatId)
        }
    }

    fun cancelVoiceReplies(before: AppSettings, after: AppSettings) = synchronized(gate) {
        val changed: (ReplyJob) -> Boolean = { job ->
            val chatId = job.message.chatId
            job.memory == null && job.draft == null &&
                before.voicePreferences(before.rules[chatId] ?: ChatRule()) !=
                after.voicePreferences(after.rules[chatId] ?: ChatRule())
        }
        ArrayList(pending.values).stream().filter { changed(it) }.forEach { job ->
            pending.remove(job.id)
            cancelJob(job)
        }
        ArrayList(active.values).stream().filter { changed(it) }.forEach(::cancelJob)
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
        if (latest[chatId] !in ids && pending.values.stream().noneMatch {
                it.message.chatId == chatId && it.message.id in ids
            } && active.values.stream().noneMatch {
                it.message.chatId == chatId && it.message.id in ids
            }
        ) {
            return@synchronized
        }
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
            if (closed) {
                return
            }
            closed = true
            cancelPending()
        }
        ticker.cancel(false)
        scheduler.shutdownNow()
        worker.shutdownNow()
        typing.close()
        history?.close()
    }
}
