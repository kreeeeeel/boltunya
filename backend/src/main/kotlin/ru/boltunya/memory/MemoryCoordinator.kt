package ru.boltunya.memory

import jakarta.inject.Inject
import ru.boltunya.ai.ModelInference
import ru.boltunya.ai.OllamaRuntime
import ru.boltunya.diagnostics.DiagnosticLog
import ru.boltunya.diagnostics.DiagnosticSource
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.GroupReplyMode
import ru.boltunya.exception.IntegrationException
import ru.boltunya.exception.impl.BackgroundInferenceDeferredException
import ru.boltunya.exception.impl.MemoryUpdateFailedException
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.telegram.TelegramGateway
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val STARTUP_DELAY_SECONDS = 30L
private const val WORK_INTERVAL_SECONDS = 15L
private const val ERROR_RETRY_SECONDS = 300L
private const val CONFIG_REFRESH_SECONDS = 60L
private const val OWNER_SOURCE_CHAT_LIMIT = 8L
private const val OWNER_SOURCE_MAX_AGE_DAYS = 90L
private const val SHUTDOWN_TIMEOUT_SECONDS = 5L

class MemoryCoordinator @Inject constructor(
    private val store: MemoryStore,
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val ollama: OllamaRuntime,
    private val processor: MemoryProcessor,
    private val inference: ModelInference,
    private val clock: Clock,
    private val automated: AutomatedReplyStore,
    private val diagnostics: DiagnosticLog? = null,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "conversation-memory").also {
            it.isDaemon =
                true
        }
    }
    private val failures = ConcurrentHashMap<String, Long>()
    private val errors = ConcurrentHashMap<String, String>()
    private val collections = ConcurrentLinkedQueue<MemoryCollectionRequest>()
    private val cancelledUntil = ConcurrentHashMap<String, Long>()
    private var lastRefresh = 0L

    @Volatile private var startup = true
    private var attachedAccount = ""

    @Volatile private var closed = false

    @Volatile private var processing = ""

    @Volatile private var waitingModel = false

    @Volatile private var groupChatIds: Set<String> = emptySet()

    @Volatile private var listedChatIds: Set<String> = emptySet()

    fun start() {
        store.initialize()
        executor.scheduleWithFixedDelay({ tick() }, STARTUP_DELAY_SECONDS, WORK_INTERVAL_SECONDS, TimeUnit.SECONDS)
    }

    fun observe(message: ChatMessage) {
        if (!settings.get().memoryEnabled || !settings.rule(message.chatId).useMemory) return
        if (!attach()) return
        val rule = settings.rule(message.chatId)
        val ownMessage =
            message.outgoing && !message.automated && !automated.contains(message.chatId, message.id) && !message.group
        if (message.group) {
            groupChatIds = groupChatIds + message.chatId
            if (store.state(message.chatId)?.manual != true && !groupEnabled(rule)) return
        }
        val state = store.state(message.chatId)
        if (!rule.enabled && state?.manual != true && state?.ownerSource != true && !ownMessage) return
        store.observe(message.chatId, message.id.toLongOrNull() ?: return, ownerSource = ownMessage)
        if (!message.outgoing && rule.enabled && !settings.get().paused) inference.interruptBackground()
    }

    fun context(rule: ChatRule, history: List<ChatMessage>): MemoryContext {
        if (!settings.get().memoryEnabled) return MemoryContext()
        val chatId = history.lastOrNull()?.chatId ?: return MemoryContext()
        if (chatId.toLongOrNull() == null) return MemoryContext()
        return try {
            if (!attach()) return MemoryContext()
            if (rule.useMemory) store.observe(chatId, history.last().id.toLongOrNull() ?: 0, manual = !rule.enabled)
            val general = store.read("")
            val chat = if (rule.useMemory) store.read(chatId) else StoredMemory("", "", "", "")
            MemoryContext(
                general.notes.takeIf { store.hasNotes("", it) }.orEmpty(),
                chat.notes.takeIf { store.hasNotes(chatId, it) }.orEmpty(),
                chat.facts(),
                general.facts(),
            )
        } catch (exception: Exception) {
            diagnostics?.record(DiagnosticSource.MEMORY, exception)
            errors[chatId] = MemoryUpdateFailedException().message
            System.err.println("Memory context unavailable: ${exception.javaClass.simpleName}")
            MemoryContext()
        }
    }

    @Synchronized
    fun refresh(chatId: String? = null, rebuild: Boolean = false, immediate: Boolean = false) {
        if (closed || !attach()) return
        if (immediate) collections.removeIf { !pending(it) && processing != it.chatId }
        if (chatId == null) {
            refreshConfigured(true)
            if (rebuild || immediate) {
                val selected = store.states().filter(::allowed)
                if (rebuild) store.rebuildOwner()
                selected.stream().forEach { state ->
                    cancelledUntil.remove(state.chatId)
                    if (rebuild) {
                        store.rebuild(
                            state.chatId,
                            manual = false,
                        )
                    } else {
                        store.observe(state.chatId, request = true)
                    }
                    failures.remove(state.chatId)
                    errors.remove(state.chatId)
                    if (immediate) {
                        collections.removeIf { it.chatId == state.chatId }
                        collections.add(
                            MemoryCollectionRequest(
                                state.chatId,
                                rebuild,
                                store.state(state.chatId)?.processedMessages ?: 0,
                            ),
                        )
                    }
                }
                if (immediate && selected.isNotEmpty()) {
                    inference.interruptBackground()
                    executor.execute { tick() }
                }
            }
        } else {
            cancelledUntil.remove(chatId)
            val chats = telegram.chats()
            listedChatIds = chats.map { it.id }.toSet()
            groupChatIds = chats.filter { it.kind != "private" }.map { it.id }.toSet()
            if (rebuild) store.rebuild(chatId) else store.observe(chatId, manual = true, request = true)
            failures.remove(chatId)
            errors.remove(chatId)
            if (immediate) {
                collections.removeIf { it.chatId == chatId }
                collections.add(MemoryCollectionRequest(chatId, rebuild, store.state(chatId)?.processedMessages ?: 0))
                inference.interruptBackground()
                executor.execute { tick() }
            }
        }
    }

    @Synchronized
    fun cancel(chatId: String) {
        if (!attach()) return
        val ids = collections.stream()
            .filter { (chatId.isEmpty() || it.chatId == chatId) && (pending(it) || processing == it.chatId) }
            .map { it.chatId }.toList()
        val resumeAt =
            clock.instant().epochSecond + TimeUnit.HOURS.toSeconds(settings.get().memoryIntervalHours.toLong())
        ids.stream().forEach { id ->
            cancelledUntil[id] = resumeAt
            store.cancelCollection(id)
            failures.remove(id)
            errors.remove(id)
        }
        collections.removeIf { it.chatId in ids }
        if (processing in ids) inference.interruptBackground()
    }

    private fun suspended(chatId: String): Boolean = clock.instant().epochSecond < (cancelledUntil[chatId] ?: 0)

    private fun pending(request: MemoryCollectionRequest): Boolean {
        val state = store.state(request.chatId) ?: return false
        return allowed(state) && !suspended(request.chatId) &&
            (state.requested || state.deltaTargetId != 0L || request.rebuild && !state.backfillComplete)
    }

    private fun collection(chatId: String): MemoryCollectionProgress {
        if (!settings.get().memoryEnabled) return MemoryCollectionProgress()
        val requests = collections.toList()
        val queue = requests.filter { pending(it) || processing == it.chatId && !suspended(it.chatId) }
        val selected = queue.filter { chatId.isEmpty() || it.chatId == chatId }
        if (selected.isEmpty()) return MemoryCollectionProgress()
        val running = selected.any { it.chatId == processing }
        val status = when {
            running -> MemoryStatus.PROCESSING
            selected.any { errors.containsKey(it.chatId) } -> MemoryStatus.ERROR
            waitingModel -> MemoryStatus.WAITING_MODEL
            else -> MemoryStatus.QUEUED
        }
        return MemoryCollectionProgress(
            active = true,
            status = status,
            queuedChats = queue.count { it.chatId != processing },
            queuePosition = if (running) 0 else queue.indexOf(selected.first()) + 1,
            processedMessages = requests.filter { chatId.isEmpty() || it.chatId == chatId }.sumOf {
                maxOf(0, (store.state(it.chatId)?.processedMessages ?: 0) - it.initialProcessedMessages)
            },
        )
    }

    fun document(chatId: String): MemoryDocument {
        attach()
        val document = store.read(chatId)
        val state = store.state(chatId)
        val all = store.states()
        val active = all.filter { allowed(it) && !suspended(it.chatId) }
        val enabled = settings.get().memoryEnabled && (chatId.isEmpty() || settings.rule(chatId).useMemory)
        val status = when {
            !enabled -> MemoryStatus.DISABLED
            chatId.isNotEmpty() && suspended(chatId) ->
                if (document.summary.isNotBlank()) MemoryStatus.READY else MemoryStatus.IDLE
            chatId.isEmpty() && processing.isNotEmpty() -> MemoryStatus.PROCESSING
            chatId.isEmpty() && waitingModel && active.isNotEmpty() -> MemoryStatus.WAITING_MODEL
            chatId.isEmpty() && active.any { !it.backfillComplete || it.dirty || it.requested } ->
                MemoryStatus.QUEUED
            chatId.isEmpty() && all.isNotEmpty() -> MemoryStatus.READY
            processing == chatId && chatId.isNotEmpty() -> MemoryStatus.PROCESSING
            errors.containsKey(chatId) -> MemoryStatus.ERROR
            waitingModel && (state?.dirty == true || state?.backfillComplete == false) -> MemoryStatus.WAITING_MODEL
            state != null && allowed(
                state,
            ) && (!state.backfillComplete || state.dirty || state.requested) -> MemoryStatus.QUEUED
            document.summary.isNotBlank() -> MemoryStatus.READY
            else -> MemoryStatus.IDLE
        }
        return MemoryDocument(
            chatId, document.notes, document.summary, document.revision,
            state?.updatedAt ?: all.maxOfOrNull { it.updatedAt } ?: 0,
            state?.processedMessages ?: all.sumOf { it.processedMessages },
            state?.backfillComplete ?: all.all { it.backfillComplete }, status, errors[chatId].orEmpty(), all.size,
            store.relativePath(
                chatId,
            ),
            collection(chatId),
        )
    }

    internal fun tick() {
        if (closed || !settings.get().memoryEnabled) return
        val now = clock.instant().epochSecond
        try {
            if (!attach()) return
            if (startup || now - lastRefresh >= CONFIG_REFRESH_SECONDS) {
                refreshConfigured(startup)
                startup = false
                lastRefresh = now
            }
            if (inference.foregroundBusy()) return
            val interval = TimeUnit.HOURS.toSeconds(settings.get().memoryIntervalHours.toLong())
            collections.removeIf { store.state(it.chatId) == null || suspended(it.chatId) }
            val priority = collections.firstOrNull(::pending)?.chatId
            val selected = store.states().stream().filter { state ->
                allowed(state) && !suspended(state.chatId) && now >= (failures[state.chatId] ?: 0) &&
                    (
                        !state.backfillComplete || state.deltaTargetId != 0L || state.requested ||
                            state.dirty && now - state.recentUpdatedAt >= interval
                        )
            }.sorted(
                compareBy<ChatMemoryState> { if (it.chatId == priority) 0 else 1 }.thenBy {
                    if (it.requested || it.deltaTargetId != 0L ||
                        it.headId == 0L || it.dirty && now - it.recentUpdatedAt >= interval
                    ) {
                        0
                    } else {
                        1
                    }
                }.thenBy { it.updatedAt },
            ).findFirst().orElse(null)
                ?: return
            val model = settings.get().defaultModel
            waitingModel = try {
                model !in ollama.installed()
            } catch (_: IntegrationException) {
                true
            }
            if (waitingModel) return
            processing = selected.chatId
            val recent =
                selected.headId == 0L || selected.deltaTargetId != 0L || selected.requested ||
                    selected.dirty && now - selected.recentUpdatedAt >= interval
            processor.process(selected, model, recent) {
                !closed && !suspended(selected.chatId) && settings.get().memoryEnabled && allowed(selected) &&
                    telegram.status().userId == selected.accountId
            }
            failures.remove(selected.chatId)
            errors.remove(selected.chatId)
        } catch (_: BackgroundInferenceDeferredException) {
            // A foreground response takes priority; the checkpoint remains unchanged.
        } catch (exception: Exception) {
            if (!closed) {
                diagnostics?.record(DiagnosticSource.MEMORY, exception)
                if (processing.isNotEmpty()) {
                    failures[processing] = now + ERROR_RETRY_SECONDS
                    errors[processing] = MemoryUpdateFailedException().message
                }
                System.err.println("Memory update failed: ${exception.javaClass.simpleName}")
            }
        } finally {
            processing = ""
        }
    }

    @Synchronized
    private fun attach(): Boolean {
        val account = telegram.status()
        if (account.state != "ready" || account.userId.isEmpty()) return false
        store.activate(account.userId)
        if (attachedAccount != account.userId) {
            attachedAccount = account.userId
            startup = true
            failures.clear()
            errors.clear()
            collections.clear()
            cancelledUntil.clear()
        }
        return true
    }

    private fun refreshConfigured(onStartup: Boolean) {
        val chats = telegram.chats()
        listedChatIds = chats.map { it.id }.toSet()
        groupChatIds = chats.filter { it.kind != "private" }.map { it.id }.toSet()
        val cutoff = clock.instant().epochSecond - TimeUnit.DAYS.toSeconds(OWNER_SOURCE_MAX_AGE_DAYS)
        val sources = chats.stream()
            .filter { it.kind == "private" && it.date > cutoff && it.date > 0 && settings.rule(it.id).useMemory }
            .sorted(compareByDescending { it.date })
            .limit(OWNER_SOURCE_CHAT_LIMIT).map { it.id }.toList().toSet()
        chats.stream().filter { chat ->
            val rule = settings.rule(chat.id)
            val state = store.state(chat.id)
            rule.useMemory &&
                (rule.enabled || state?.manual == true || state?.ownerSource == true || chat.id in sources) &&
                (chat.kind == "private" || store.state(chat.id)?.manual == true || groupEnabled(rule))
        }.forEach { chat ->
            val id = chat.lastMessageId.toLongOrNull() ?: 0
            val previous = store.state(chat.id)
            store.observe(
                chat.id,
                id,
                request = onStartup && (previous == null || id > previous.headId),
                ownerSource = chat.id in sources,
            )
        }
    }

    private fun allowed(state: ChatMemoryState): Boolean {
        val rule = settings.rule(state.chatId)
        return state.chatId in listedChatIds && rule.useMemory && (rule.enabled || state.manual || state.ownerSource) &&
            (state.chatId !in groupChatIds || state.manual || groupEnabled(rule))
    }

    private fun groupEnabled(rule: ChatRule): Boolean =
        (if (rule.groupReplies == GroupReplyMode.INHERIT) settings.get().groupReplies else rule.groupReplies) !=
            GroupReplyMode.DISABLED

    override fun close() {
        closed = true
        executor.shutdownNow()
        inference.interruptBackground()
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("Conversation memory worker did not stop within the shutdown timeout")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            System.err.println("Conversation memory shutdown was interrupted")
        }
    }
}
