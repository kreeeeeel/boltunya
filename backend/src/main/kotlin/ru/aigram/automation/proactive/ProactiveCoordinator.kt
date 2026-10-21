package ru.aigram.automation.proactive

import ru.aigram.automation.ReplyCoordinator
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.diagnostics.DiagnosticSource
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ChatRule
import ru.aigram.domain.ChatSummary
import ru.aigram.domain.GroupReplyMode
import ru.aigram.domain.ProactiveMode
import ru.aigram.domain.ReplyResult
import ru.aigram.resources.ResourceCatalog
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TICK_SECONDS = 30L

/** Opt-in idle follow-ups share the reply queue; a completed initiative waits for a real incoming message. */
class ProactiveCoordinator(
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val replies: ReplyCoordinator,
    private val ledger: ProactiveLedger,
    private val clock: Clock,
    private val resources: ResourceCatalog,
    private val diagnostics: DiagnosticLog,
    private val generate: (ChatRule, List<ChatMessage>) -> ReplyResult,
) : AutoCloseable {
    private val closed = AtomicBoolean()
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "conversation-initiative").also { it.isDaemon = true }
    }
    private val observations = mutableMapOf<String, ProactiveObservation>()
    private var owner = ""

    fun start() {
        executor.scheduleWithFixedDelay(::check, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS)
    }

    fun observe(message: ChatMessage) {
        val account = telegram.status().userId
        try {
            if (account.isNotEmpty()) ledger.observe(account, message)
        } catch (exception: Exception) {
            diagnostics.record(DiagnosticSource.REPLY, exception)
            System.err.println("Conversation checkpoint unavailable: ${exception.javaClass.simpleName}")
        }
    }

    private fun check() {
        try {
            tick()
        } catch (exception: Exception) {
            diagnostics.record(DiagnosticSource.REPLY, exception)
            System.err.println("Conversation initiative unavailable: ${exception.javaClass.simpleName}")
        }
    }

    @Synchronized
    internal fun tick() {
        val account = telegram.status()
        if (owner != account.userId) {
            owner = account.userId
            observations.clear()
        }
        if (account.state != "ready" || owner.isEmpty() || settings.get().paused || replies.queue().paused) {
            observations.clear()
            return
        }
        val now = clock.instant().epochSecond
        val eligible = telegram.chats().filter { eligible(it, settings.rule(it.id)) }
        observations.keys.retainAll(eligible.map { it.id }.toSet())
        eligible.stream().anyMatch { chat ->
            val previous = observations[chat.id]
            if (previous == null || previous.messageId != chat.lastMessageId) {
                observations[chat.id] = ProactiveObservation(chat.lastMessageId, now)
                false
            } else {
                enqueue(chat, previous, now, owner)
            }
        }
    }

    private fun enqueue(chat: ChatSummary, observation: ProactiveObservation, now: Long, account: String): Boolean {
        val rule = settings.rule(chat.id)
        val minutes = rule.proactiveIntervalMinutes.takeIf { it > 0 } ?: settings.get().proactiveIntervalMinutes
        val previous = ledger.get(account, chat.id)
        if (previous?.awaitingResponse == true || replies.hasWork(chat.id) || replies.hasInitiative() ||
            now - maxOf(observation.since, previous?.attemptedAt ?: 0) < TimeUnit.MINUTES.toSeconds(minutes.toLong())
        ) {
            return false
        }
        val reservation = ledger.reserve(account, chat.id, chat.lastMessageId, now)
        val task = ProactiveTask(
            allowed = {
                telegram.status().userId == account && !settings.get().paused &&
                    telegram.chats().any {
                        it.id == chat.id && it.lastMessageId == chat.lastMessageId && eligible(it, settings.rule(it.id))
                    } && ledger.get(account, chat.id)?.lastIncomingId == reservation.lastIncomingId
            },
            generate = generate,
            prepareDelivery = { ledger.prepareDelivery(account, chat.id, reservation) },
            trigger = resources.text("activity/ru/initiative.txt"),
        )
        return replies.enqueueInitiative(chat, account, task)
    }

    private fun eligible(chat: ChatSummary, rule: ChatRule): Boolean {
        val enabled = when (rule.proactiveMode) {
            ProactiveMode.ENABLED -> true
            ProactiveMode.DISABLED -> false
            ProactiveMode.INHERIT -> settings.get().proactiveEnabled
        }
        val groupMode = if (rule.groupReplies ==
            GroupReplyMode.INHERIT
        ) {
            settings.get().groupReplies
        } else {
            rule.groupReplies
        }
        return enabled && rule.enabled && chat.id != owner && chat.joined != false && chat.kind != "channel" &&
            chat.lastMessageId.isNotEmpty() && chat.lastMessageOutgoing && chat.unread == 0 &&
            chat.typingUsers.isEmpty() &&
            (
                chat.kind == "private" || groupMode == GroupReplyMode.ALL ||
                    groupMode == GroupReplyMode.MENTIONS && rule.proactiveMode == ProactiveMode.ENABLED
                )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        executor.shutdownNow()
    }
}
