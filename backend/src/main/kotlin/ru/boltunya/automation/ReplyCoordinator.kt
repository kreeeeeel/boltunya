package ru.boltunya.automation

import ru.boltunya.domain.ActivityEntry
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.GroupReplyMode
import ru.boltunya.domain.ReplyResult
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ReplyCoordinator(
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val automated: AutomatedReplyStore? = null,
    private val flows: FlowEngine? = null,
    private val generate: (ChatRule, List<ChatMessage>) -> ReplyResult,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val pending = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val latest = ConcurrentHashMap<String, String>()
    private val entries = CopyOnWriteArrayList<ActivityEntry>()
    private val typing = TypingIndicator(telegram)
    private var nextReplyAt = 0L
    private val epoch = AtomicLong()
    private val tasks = AssistantTaskFilter()

    @Volatile private var closed = false

    fun activities(): List<ActivityEntry> = entries.reversed().take(100)

    fun onMessage(message: ChatMessage) {
        if (closed) return
        typing.cancel(message.chatId)
        latest[message.chatId] = message.id
        pending.remove(message.chatId)?.cancel(false)
        val rule = settings.rule(message.chatId)
        if (message.deleted || message.outgoing || message.text.isBlank() || settings.get().paused ||
            (!rule.enabled && flows?.matches(message) != true) ||
            !groupAllowed(message, rule)
        ) {
            return
        }
        if (settings.get().ignoreAssistantTasks && tasks.ignores(message.text) &&
            flows?.matches(message) != true
        ) {
            return
        }
        val delay = (rule.delaySeconds + settings.get().replyDelaySeconds) * 1000L + 800L
        pending[message.chatId] = executor.schedule({ respond(message) }, delay, TimeUnit.MILLISECONDS)
    }

    private fun respond(message: ChatMessage) {
        val rule = settings.rule(message.chatId)
        if (!allowed(message, rule)) {
            return
        }
        val wait = nextReplyAt - System.nanoTime()
        if (wait > 0) {
            pending[message.chatId] = executor.schedule({ respond(message) }, wait, TimeUnit.NANOSECONDS)
            return
        }
        val chat = telegram.chats().stream().filter { it.id == message.chatId }.findFirst().orElse(null)
        if (chat == null) {
            return
        }
        val entry =
            ActivityEntry(
                UUID.randomUUID().toString(),
                message.chatId,
                chat.title,
                "generating",
                rule.model,
                Instant.now().epochSecond,
            )
        entries.add(entry)
        val preferences = settings.get()
        val generationEpoch = epoch.get()
        val indicator = if (preferences.showTyping) {
            typing.start(message.chatId) { allowed(message, rule) && settings.get().showTyping }
        } else {
            null
        }
        try {
            val history = telegram.history(message.chatId, if (rule.matchMyStyle) 100 else rule.contextMessages)
            val context = if (history.stream().anyMatch { it.id == message.id }) {
                history
            } else {
                history + message
            }
            val globalPrompt = settings.get().globalPrompt
            val execution = flows?.execute(message, rule, context)
            val result = execution?.reply ?: generate(rule, context)
            if (!allowed(message, rule) || settings.rule(message.chatId) != rule ||
                settings.get().globalPrompt != globalPrompt ||
                epoch.get() != generationEpoch
            ) {
                replace(entry, "cancelled", ReplyCancelledException().message)
                return
            }
            if (execution != null &&
                result.text.isBlank()
            ) {
                flows?.commit(execution)
                replace(entry, "sent", rule.model, result.durationMs)
                return
            }
            val sentId = telegram.send(message.chatId, result.text, message.id, message.topicId)
            nextReplyAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(settings.get().replyIntervalSeconds.toLong())
            automated?.record(message.chatId, sentId)
            if (execution != null) flows?.commit(execution)
            replace(entry, "sent", rule.model, result.durationMs)
        } catch (exception: Exception) {
            replace(
                entry,
                "error",
                if (exception is TelegramRateLimitException) {
                    exception.message
                } else {
                    ReplyDeliveryFailedException().message
                },
            )
            System.err.println("Auto-reply failed: ${exception.javaClass.simpleName}")
            val retryAfter = if (exception is TelegramRateLimitException) exception.retryAfterSeconds else 30L
            nextReplyAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(retryAfter)
        } finally {
            typing.stop(message.chatId, indicator)
        }
    }

    private fun allowed(message: ChatMessage, rule: ChatRule): Boolean =
        !settings.get().paused && (rule.enabled || flows?.matches(message) == true) &&
            latest[message.chatId] == message.id &&
            groupAllowed(message, rule)

    private fun groupAllowed(message: ChatMessage, rule: ChatRule): Boolean {
        if (!message.group) return true
        val mode = if (rule.groupReplies == GroupReplyMode.INHERIT) settings.get().groupReplies else rule.groupReplies
        return mode == GroupReplyMode.ALL || mode == GroupReplyMode.MENTIONS && message.mentionsMe
    }

    fun cancelPending() {
        epoch.incrementAndGet()
        pending.values.stream().forEach { it.cancel(false) }
        pending.clear()
        typing.cancelAll()
    }

    fun onDeleted(chatId: String, ids: Set<String>) {
        if (latest[chatId] !in ids) return
        latest.remove(chatId)
        pending.remove(chatId)?.cancel(false)
        typing.cancel(chatId)
    }

    private fun replace(entry: ActivityEntry, status: String, detail: String, duration: Long = 0) {
        entries.removeIf { it.id == entry.id }
        entries.add(entry.copy(status = status, detail = detail, durationMs = duration))
        if (entries.size > 100) {
            entries.removeAt(0)
        }
    }

    override fun close() {
        closed = true
        cancelPending()
        executor.shutdownNow()
        typing.close()
    }
}
