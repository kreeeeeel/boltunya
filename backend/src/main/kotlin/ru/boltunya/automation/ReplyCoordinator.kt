package ru.boltunya.automation

import ru.boltunya.automation.activity.ActivityLog
import ru.boltunya.automation.activity.ActivityPhase
import ru.boltunya.automation.activity.ActivityStatus
import ru.boltunya.automation.activity.ReplyJob
import ru.boltunya.automation.activity.ReplyTraceScope
import ru.boltunya.diagnostics.DiagnosticLog
import ru.boltunya.diagnostics.DiagnosticSource
import ru.boltunya.domain.ActivityDetails
import ru.boltunya.domain.ActivityEntry
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.GroupReplyMode
import ru.boltunya.domain.ReplyQueueState
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.AppException
import ru.boltunya.exception.impl.ActivityDeliveryStartedException
import ru.boltunya.exception.impl.ActivityNotFoundException
import ru.boltunya.exception.impl.ReplyCancelledException
import ru.boltunya.exception.impl.ReplyDeliveryFailedException
import ru.boltunya.exception.impl.TelegramRateLimitException
import ru.boltunya.flows.FlowEngine
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.telegram.TelegramGateway
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val MESSAGE_DEBOUNCE_MS = 800L
private const val QUEUE_TICK_MS = 100L
private const val FAILURE_BACKOFF_SECONDS = 30L
private const val STYLE_HISTORY_MESSAGES = 100

class ReplyCoordinator(
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val automated: AutomatedReplyStore? = null,
    private val flows: FlowEngine? = null,
    private val tracing: ReplyTraceScope = ReplyTraceScope(),
    private val diagnostics: DiagnosticLog? = null,
    private val generate: (ChatRule, List<ChatMessage>) -> ReplyResult,
) : AutoCloseable {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val worker = Executors.newSingleThreadExecutor()
    private val gate = Any()
    private val pending = linkedMapOf<String, ReplyJob>()
    private val latest = ConcurrentHashMap<String, String>()
    private val log = ActivityLog()
    private val typing = TypingIndicator(telegram)
    private val epoch = AtomicLong()
    private val tasks = AssistantTaskFilter()
    private var order = 0L
    private var priorityOrder = 0L
    private var active: ReplyJob? = null
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
        pending.values.stream().sorted(compareBy { it.order }).forEach { positions[it.id] = positions.size + 1 }
        log.summaries().stream().map { entry ->
            val job = pending[entry.id]
            entry.copy(
                queuePosition = positions[entry.id] ?: 0,
                priority = job?.priority ?: false,
                waitUntil = job?.waitUntil ?: 0,
            )
        }.toList()
    }

    fun queue(): ReplyQueueState = synchronized(gate) {
        ReplyQueueState(queuePaused, pending.size, if (active != null) 1 else 0)
    }

    fun details(id: String): ActivityDetails = synchronized(gate) {
        val details = log.details(id)
        val job = pending[id]
        val position = if (job ==
            null
        ) {
            0
        } else {
            pending.values.stream().filter { it.order < job.order }.count().toInt() + 1
        }
        details.copy(
            entry = details.entry.copy(
                queuePosition = position,
                priority = job?.priority ?: false,
                waitUntil = job?.waitUntil ?: 0,
            ),
        )
    }
    fun pauseQueue(paused: Boolean) = synchronized(gate) { queuePaused = paused }
    fun clearCompleted() = log.clearCompleted()

    fun prioritize(id: String) = synchronized(gate) {
        val job = pending[id] ?: throw ActivityNotFoundException()
        job.order = --priorityOrder
        job.priority = true
    }

    fun cancel(id: String) = synchronized(gate) {
        val job = pending.remove(id) ?: active?.takeIf { it.id == id }
        if (job == null) {
            log.details(id)
            return@synchronized
        }
        if (job.delivering) throw ActivityDeliveryStartedException()
        cancelJob(job)
    }

    fun onMessage(message: ChatMessage) = synchronized(gate) {
        if (closed) return@synchronized
        typing.cancel(message.chatId)
        latest[message.chatId] = message.id
        ArrayList(pending.values).stream().filter { it.message.chatId == message.chatId }.forEach {
            pending.remove(it.id)
            cancelJob(it)
        }
        active?.takeIf { it.message.chatId == message.chatId }?.let(::cancelJob)
        val preferences = settings.get()
        val rule = settings.rule(message.chatId)
        if (message.deleted || message.outgoing || message.text.isBlank() || preferences.paused ||
            (!rule.enabled && flows?.matches(message) != true) || !groupAllowed(message, rule)
        ) {
            return@synchronized
        }
        if (preferences.ignoreAssistantTasks && tasks.ignores(message.text) && flows?.matches(message) != true) {
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
    }

    private fun dispatch() = synchronized(gate) {
        if (closed || queuePaused || settings.get().paused || active != null || System.nanoTime() < nextReplyAt) {
            return@synchronized
        }
        val job =
            pending.values.stream().filter { System.nanoTime() >= it.readyAt }.min(compareBy { it.order }).orElse(null)
                ?: return@synchronized
        pending.remove(job.id)
        active = job
        worker.execute { respond(job) }
    }

    private fun respond(job: ReplyJob) {
        job.thread = Thread.currentThread()
        val message = job.message
        val rule = settings.rule(message.chatId)
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
                val execution = flows?.execute(message, rule, context)
                val result = execution?.reply ?: generate(rule, context)
                tracing.phase(ActivityPhase.VALIDATING)
                // Commit delivery under the queue lock; never hold it across a blocking TDLib call.
                synchronized(gate) {
                    if (job.cancelled.get() || !allowed(message, rule) || settings.rule(message.chatId) != rule ||
                        settings.get().globalPrompt != globalPrompt || epoch.get() != generationEpoch
                    ) {
                        throw ReplyCancelledException()
                    }
                    tracing.phase(ActivityPhase.SENDING)
                    job.delivering = true
                }
                if (result.text.isNotBlank() || execution == null) {
                    val sentId = telegram.send(message.chatId, result.text, message.id, message.topicId)
                    synchronized(gate) {
                        nextReplyAt =
                            System.nanoTime() + TimeUnit.SECONDS.toNanos(settings.get().replyIntervalSeconds.toLong())
                    }
                    automated?.record(message.chatId, sentId)
                }
                if (execution != null) flows?.commit(execution)
                job.trace.finish(ActivityStatus.SENT, settings.resolve(rule).model, result.text)
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
                val retryAfter = if (exception is TelegramRateLimitException) {
                    exception.retryAfterSeconds
                } else {
                    FAILURE_BACKOFF_SECONDS
                }
                synchronized(gate) { nextReplyAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(retryAfter) }
            }
        } finally {
            Thread.interrupted()
            typing.stop(message.chatId, indicator)
            job.thread = null
            synchronized(gate) { if (active === job) active = null }
            log.prune()
        }
    }

    private fun cancelJob(job: ReplyJob) {
        if (job.delivering || !job.trace.active()) return
        job.cancel()
        job.trace.finish(ActivityStatus.CANCELLED, ReplyCancelledException().message)
        typing.cancel(job.message.chatId)
    }

    private fun allowed(message: ChatMessage, rule: ChatRule): Boolean =
        !closed && !settings.get().paused && (rule.enabled || flows?.matches(message) == true) &&
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
        active?.let(::cancelJob)
        typing.cancelAll()
    }

    fun onDeleted(chatId: String, ids: Set<String>) = synchronized(gate) {
        if (latest[chatId] !in ids) return@synchronized
        latest.remove(chatId)
        ArrayList(pending.values).stream().filter { it.message.chatId == chatId }.forEach {
            pending.remove(it.id)
            cancelJob(it)
        }
        active?.takeIf { it.message.chatId == chatId }?.let(::cancelJob)
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
