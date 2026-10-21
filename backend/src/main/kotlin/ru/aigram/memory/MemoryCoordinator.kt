package ru.aigram.memory

import org.springframework.beans.factory.ObjectProvider
import ru.aigram.ai.ModelInference
import ru.aigram.ai.OllamaRuntime
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.automation.MemoryQueueTask
import ru.aigram.automation.MemoryWorkQueue
import ru.aigram.diagnostics.DiagnosticLog
import ru.aigram.domain.ai.profile.ModelProvider
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.chat.GroupReplyMode
import ru.aigram.domain.events.diagnostics.DiagnosticSource
import ru.aigram.domain.memory.ChatMemoryState
import ru.aigram.domain.memory.MemoryCollectionProgress
import ru.aigram.domain.memory.MemoryCollectionRequest
import ru.aigram.domain.memory.MemoryCollectionScope
import ru.aigram.domain.memory.MemoryContext
import ru.aigram.domain.memory.MemoryDocument
import ru.aigram.domain.memory.MemoryStatus
import ru.aigram.domain.memory.StoredMemory
import ru.aigram.exception.IntegrationException
import ru.aigram.exception.impl.BackgroundInferenceDeferredException
import ru.aigram.exception.impl.MemoryUpdateFailedException
import ru.aigram.exception.impl.ReplyCancelledException
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import java.time.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Stream

private const val STARTUP_DELAY_SECONDS = 30L
private const val WORK_INTERVAL_SECONDS = 15L
private const val ERROR_RETRY_SECONDS = 300L
private const val CONFIG_REFRESH_SECONDS = 60L
private const val SHUTDOWN_TIMEOUT_SECONDS = 5L
private const val NO_PROGRESS_RETRY_MILLIS = 10000L

class MemoryCoordinator(
    private val store: MemoryStore,
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val ollama: OllamaRuntime,
    private val processor: MemoryProcessor,
    private val inference: ModelInference,
    private val clock: Clock,
    private val automated: AutomatedReplyStore,
    private val queueProvider: ObjectProvider<MemoryWorkQueue>,
    private val diagnostics: DiagnosticLog? = null,
    private val targets: ModelTargetResolver? = null,
    private val roster: ParticipantRosterStore? = null,
    private val exclusions: ContextExclusionStore? = null,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "conversation-memory").also {
            it.isDaemon =
                true
        }
    }
    private val queue by lazy { queueProvider.getObject() }
    private val outstanding = AtomicReference("")
    private val completion = AtomicReference<CountDownLatch?>()

    @Volatile private var queuedChat = ""
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
        if (!settings.get().memoryEnabled || !settings.rule(message.chatId).useMemory) {
            return
        }
        if (!attach()) {
            return
        }
        val rule = settings.rule(message.chatId)
        if (!collectionAllowed(message.chatId, rule, message.group) ||
            message.outgoing && (message.automated || automated.contains(message.chatId, message.id))
        ) {
            return
        }
        if (message.group) {
            groupChatIds = groupChatIds + message.chatId
        }
        if (message.group && exclusions?.excludes(attachedAccount, message.chatId, message.id) != true) {
            roster?.record(attachedAccount, message.chatId, listOf(message))
        }
        store.observe(message.chatId, message.id.toLongOrNull() ?: return)
    }

    fun context(rule: ChatRule, history: List<ChatMessage>): MemoryContext {
        if (!settings.get().memoryEnabled) {
            return MemoryContext()
        }
        val chatId = history.lastOrNull()?.chatId

        if (chatId == null) {
            return MemoryContext()
        }

        if (chatId.toLongOrNull() == null) {
            return MemoryContext()
        }
        return try {
            if (!attach()) {
                return MemoryContext()
            }
            if (collectionAllowed(chatId, rule, history.any { it.group })) {
                store.observe(
                    chatId,
                    history.last().id.toLongOrNull() ?: 0,
                )
            }
            if (collectionAllowed(
                    chatId,
                    rule,
                    history.any {
                        it.group
                    },
                )
            ) {
                roster?.record(attachedAccount, chatId, history)
            }
            val general = store.read("")
            val chat = if (rule.useMemory) {
                store.read(chatId)
            } else {
                StoredMemory("", "", "", "")
            }
            MemoryContext(
                general.notes.takeIf { store.hasNotes("", it) }.orEmpty(),
                chat.notes.takeIf { store.hasNotes(chatId, it) }.orEmpty(),
                chat.facts(),
                general.facts(),
                if (rule.useMemory &&
                    history.any { it.group }
                ) {
                    roster?.context(attachedAccount, chatId).orEmpty()
                } else {
                    emptyList()
                },
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
        if (closed || !attach()) {
            return
        }
        if (immediate) {
            collections.removeIf { !pending(it) && processing != it.chatId }
        }
        if (chatId == null) {
            refreshConfigured()
            val selected = store.states().filter(::automaticallyAllowed).filter {
                rebuild || immediate || hasWork(it)
            }
            if (rebuild) {
                store.rebuildOwner()
            }
            selected.stream().forEach { state ->
                cancelledUntil.remove(state.chatId)
                if (rebuild) {
                    store.rebuild(state.chatId, manual = false)
                } else {
                    store.observe(state.chatId, request = true)
                }
                failures.remove(state.chatId)
                errors.remove(state.chatId)
                collections.removeIf { it.chatId == state.chatId }
                collections.add(
                    MemoryCollectionRequest(state.chatId, rebuild, store.state(state.chatId)?.processedMessages ?: 0),
                )
            }
            if (immediate && selected.isNotEmpty()) {
                scheduleNext()
            }
        } else {
            cancelledUntil.remove(chatId)
            readDirectory()
            if (rebuild) {
                store.rebuild(chatId)
            } else {
                store.observe(chatId, manual = true, request = true)
            }
            failures.remove(chatId)
            errors.remove(chatId)
            collections.removeIf { it.chatId == chatId }
            collections.add(MemoryCollectionRequest(chatId, rebuild, store.state(chatId)?.processedMessages ?: 0))
            if (immediate) {
                scheduleNext()
            }
        }
    }

    fun pause() {
        val id = outstanding.get()
        if (id.isNotEmpty()) {
            queue.cancelMemory(id, userInitiated = false)
        }
    }

    @Synchronized
    fun invalidateContext(chatId: String): Boolean {
        if (closed || !attach()) {
            return false
        }
        if (store.state(chatId) == null && store.read(chatId).summary.isBlank()) {
            return false
        }
        cancel(chatId)
        store.rebuildOwner()
        store.rebuild(chatId, manual = false)
        store.cancelCollection(chatId)
        return true
    }

    @Synchronized
    fun cancel(chatId: String) {
        if (!attach()) {
            return
        }
        val ids = Stream.concat(
            collections.stream().filter {
                (chatId.isEmpty() || it.chatId == chatId) &&
                    (pending(it) || processing == it.chatId)
            }.map { it.chatId },
            Stream.of(queuedChat).filter { it.isNotEmpty() && (chatId.isEmpty() || it == chatId) },
        ).distinct().toList()
        ids.stream().forEach(::suspendCollection)
        if (queuedChat in ids) {
            val id = outstanding.get()
            if (id.isNotEmpty()) {
                queue.cancelMemory(id)
            }
        }
    }

    private fun suspended(chatId: String): Boolean = clock.instant().epochSecond < (cancelledUntil[chatId] ?: 0)

    private fun pending(request: MemoryCollectionRequest): Boolean {
        val state = store.state(request.chatId)

        if (state == null) {
            return false
        }

        return allowed(state) && !suspended(request.chatId) &&
            (state.requested || state.deltaTargetId != 0L || !state.backfillComplete)
    }

    private fun collection(chatId: String): MemoryCollectionProgress {
        if (!settings.get().memoryEnabled) {
            return MemoryCollectionProgress()
        }
        val requests = collections.toList()
        val queue = requests.filter { pending(it) || processing == it.chatId && !suspended(it.chatId) }
        val selected = queue.filter { chatId.isEmpty() || it.chatId == chatId }
        if (selected.isEmpty()) {
            return MemoryCollectionProgress()
        }
        val workerPosition = queueMemoryPosition()
        val running = selected.any { it.chatId == processing } && workerPosition == 0
        val status = when {
            running -> MemoryStatus.PROCESSING
            selected.any { errors.containsKey(it.chatId) } -> MemoryStatus.ERROR
            waitingModel -> MemoryStatus.WAITING_MODEL
            else -> MemoryStatus.QUEUED
        }
        return MemoryCollectionProgress(
            active = true,
            status = status,
            queuedChats = queue.count { it.chatId != processing || workerPosition > 0 },
            queuePosition = when {
                running -> 0
                selected.any { it.chatId == queuedChat } && outstanding.get().isNotEmpty() ->
                    this.queue.memoryQueuePosition(outstanding.get())
                else -> queue.indexOf(selected.first()) + 1
            },
            processedMessages = requests.filter { chatId.isEmpty() || it.chatId == chatId }.sumOf {
                maxOf(0, (store.state(it.chatId)?.processedMessages ?: 0) - it.initialProcessedMessages)
            },
        )
    }

    private fun queueMemoryPosition(): Int =
        outstanding.get().takeIf(String::isNotEmpty)?.let(queue::memoryQueuePosition) ?: 0

    fun document(chatId: String): MemoryDocument {
        attach()
        val document = store.read(chatId)
        val state = store.state(chatId)
        val all = store.states()
        val now = clock.instant().epochSecond
        val active = all.filter { scheduled(it, now) || it.chatId == queuedChat && outstanding.get().isNotEmpty() }
        val enabled = settings.get().memoryEnabled && (chatId.isEmpty() || settings.rule(chatId).useMemory)
        val status = when {
            !enabled -> MemoryStatus.DISABLED
            chatId.isNotEmpty() && suspended(chatId) ->
                if (document.summary.isNotBlank()) {
                    MemoryStatus.READY
                } else {
                    MemoryStatus.IDLE
                }
            chatId.isEmpty() && processing.isNotEmpty() && queueMemoryPosition() == 0 -> MemoryStatus.PROCESSING
            chatId.isEmpty() && waitingModel && active.isNotEmpty() -> MemoryStatus.WAITING_MODEL
            chatId.isEmpty() && active.any { !it.backfillComplete || it.dirty || it.requested } ->
                MemoryStatus.QUEUED
            chatId.isEmpty() && all.isNotEmpty() -> MemoryStatus.READY
            processing == chatId && chatId.isNotEmpty() && queueMemoryPosition() == 0 -> MemoryStatus.PROCESSING
            errors.containsKey(chatId) -> MemoryStatus.ERROR
            waitingModel && state != null && scheduled(state, now) -> MemoryStatus.WAITING_MODEL
            state != null && (scheduled(state, now) || queuedChat == chatId && outstanding.get().isNotEmpty()) ->
                MemoryStatus.QUEUED
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
        if (closed || !settings.get().memoryEnabled || outstanding.get().isNotEmpty()) {
            return
        }
        val now = clock.instant().epochSecond
        try {
            if (!attach()) {
                return
            }
            if (startup || now - lastRefresh >= CONFIG_REFRESH_SECONDS) {
                refreshConfigured()
                startup = false
                lastRefresh = now
            }
            val memoryTarget = targets?.resolve(ModelTask.MEMORY, ChatRule())
            val interval = TimeUnit.HOURS.toSeconds(settings.get().memoryIntervalHours.toLong())
            collections.removeIf { store.state(it.chatId) == null || suspended(it.chatId) }
            val priority = collections.firstOrNull(::pending)?.chatId
            val selected = store.states().stream().filter { state ->
                scheduled(state, now)
            }.sorted(
                compareBy<ChatMemoryState> {
                    if (it.chatId == priority) {
                        0
                    } else {
                        1
                    }
                }.thenBy {
                    if (it.requested || it.deltaTargetId != 0L ||
                        it.headId == 0L || it.dirty && now - it.recentUpdatedAt >= interval
                    ) {
                        0
                    } else {
                        1
                    }
                }.thenBy { it.updatedAt },
            ).findFirst().orElse(null)

            if (selected == null) {
                return
            }

            val model = memoryTarget?.model ?: settings.get().defaultModel
            waitingModel = try {
                if (memoryTarget?.provider == ModelProvider.OPENAI_COMPATIBLE) {
                    false
                } else {
                    val engine = if (memoryTarget != null && memoryTarget.profileId.isNotEmpty()) {
                        OllamaRuntime(memoryTarget.baseUrl)
                    } else {
                        ollama
                    }
                    model !in engine.installed()
                }
            } catch (_: IntegrationException) {
                true
            }
            if (waitingModel) {
                return
            }
            val explicit = collections.stream().anyMatch { it.chatId == selected.chatId && pending(it) }
            val chat = telegram.chats().stream().filter { it.id == selected.chatId }.findFirst().orElse(null)
            val id = UUID.randomUUID().toString()
            val finished = CountDownLatch(1)
            if (!outstanding.compareAndSet("", id)) {
                return
            }
            completion.set(finished)
            queuedChat = selected.chatId
            var automaticStarted = false
            val request = MemoryQueueTask(
                id, selected.chatId, selected.accountId, chat?.title ?: "", chat?.avatarUrl.orEmpty(), model, explicit,
                canStart = { !inference.foregroundBusy(memoryTarget?.executionGroup ?: "shared") },
                action = {
                    processing = selected.chatId
                    if (closed || suspended(selected.chatId) || !settings.get().memoryEnabled ||
                        telegram.status().userId != selected.accountId
                    ) {
                        throw ReplyCancelledException()
                    }
                    val saved = store.state(selected.chatId)

                    if (saved == null) {
                        throw ReplyCancelledException()
                    }

                    var current: ChatMemoryState = saved
                    fun canContinue(): Boolean {
                        val snapshot = current
                        return !closed && !Thread.currentThread().isInterrupted &&
                            !suspended(snapshot.chatId) && settings.get().memoryEnabled && allowed(snapshot) &&
                            snapshot.accountId == selected.accountId && telegram.status().userId == selected.accountId
                    }

                    if (!canContinue() || !hasWork(current)) {
                        throw ReplyCancelledException()
                    }
                    if (!explicit && !automaticStarted) {
                        val startedAt = clock.instant().epochSecond
                        if (!automaticDue(current, startedAt)) {
                            throw ReplyCancelledException()
                        }
                        val attempted = store.recordAutomaticAttempt(current, startedAt)
                        if (attempted == null) {
                            throw ReplyCancelledException()
                        }

                        current = attempted
                        automaticStarted = true
                    }
                    val currentInterval = TimeUnit.HOURS.toSeconds(settings.get().memoryIntervalHours.toLong())
                    val recent = current.headId == 0L || current.deltaTargetId != 0L || current.requested ||
                        current.dirty && clock.instant().epochSecond - current.recentUpdatedAt >= currentInterval

                    if (!processor.process(current, model, recent, ::canContinue)) {
                        if (!canContinue()) {
                            throw ReplyCancelledException()
                        }
                        throw BackgroundInferenceDeferredException(NO_PROGRESS_RETRY_MILLIS)
                    }
                },
                userCancelled = { suspendCollection(selected.chatId) },
                completed = { failure -> complete(id, selected, failure, finished) },
            )
            try {
                queue.enqueueMemory(request)
            } catch (exception: Exception) {
                request.finish(exception)
                throw exception
            }
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
        }
    }

    private fun complete(id: String, state: ChatMemoryState, failure: Throwable?, finished: CountDownLatch) {
        try {
            if (attachedAccount == state.accountId) {
                if (failure == null || failure is BackgroundInferenceDeferredException ||
                    failure is ReplyCancelledException
                ) {
                    failures.remove(state.chatId)
                    errors.remove(state.chatId)
                } else if (!closed) {
                    failures[state.chatId] = clock.instant().epochSecond + ERROR_RETRY_SECONDS
                    errors[state.chatId] = MemoryUpdateFailedException().message
                }
            }
        } finally {
            if (outstanding.get() == id) {
                processing = ""
                queuedChat = ""
                outstanding.compareAndSet(id, "")
            }
            finished.countDown()
        }
        val retryable = failure == null || failure is BackgroundInferenceDeferredException ||
            failure is ReplyCancelledException
        if (retryable &&
            !closed && collections.stream().anyMatch { pending(it) }
        ) {
            scheduleNext()
        }
    }

    private fun scheduleNext() = synchronized(executor) {
        if (!closed) {
            executor.execute { tick() }
        }
    }

    private fun suspendCollection(chatId: String) {
        val cancelledAt = clock.instant().epochSecond
        cancelledUntil[chatId] =
            cancelledAt + TimeUnit.HOURS.toSeconds(settings.get().memoryIntervalHours.toLong())
        store.cancelCollection(chatId, attemptedAt = cancelledAt)
        failures.remove(chatId)
        errors.remove(chatId)
        collections.removeIf { it.chatId == chatId }
    }

    @Synchronized
    private fun attach(): Boolean {
        val account = telegram.status()
        if (account.state != "ready" || account.userId.isEmpty()) {
            return false
        }
        store.activate(account.userId)
        if (attachedAccount != account.userId) {
            attachedAccount = account.userId
            startup = true
            failures.clear()
            errors.clear()
            collections.clear()
            cancelledUntil.clear()
            readDirectory()
        }
        return true
    }

    private fun readDirectory(): List<ChatSummary> {
        val chats = telegram.chats()
        listedChatIds = chats.filter { it.kind != "channel" }.map { it.id }.toSet()
        groupChatIds = chats.filter { it.kind != "private" }.map { it.id }.toSet()
        return chats
    }

    private fun refreshConfigured() {
        val chats = readDirectory()
        chats.stream().filter { chat ->
            val rule = settings.rule(chat.id)
            collectionAllowed(chat.id, rule, chat.kind != "private")
        }.forEach { chat ->
            val id = chat.lastMessageId.toLongOrNull() ?: 0
            store.observe(chat.id, id)
        }
    }

    private fun automaticDue(state: ChatMemoryState, now: Long): Boolean {
        val previous = maxOf(state.automaticAttemptAt, state.updatedAt, state.recentUpdatedAt)
        val interval = TimeUnit.HOURS.toSeconds(settings.get().memoryIntervalHours.toLong())
        return previous == 0L || now - previous >= interval
    }

    private fun scheduled(state: ChatMemoryState, now: Long): Boolean {
        if (!allowed(state) || suspended(state.chatId) || now < (failures[state.chatId] ?: 0)) {
            return false
        }
        val explicit = collections.stream().anyMatch { it.chatId == state.chatId && pending(it) }
        return hasWork(state) && (explicit || automaticDue(state, now))
    }

    private fun hasWork(state: ChatMemoryState): Boolean =
        !state.backfillComplete || state.deltaTargetId != 0L || state.requested || state.dirty

    private fun automaticallyAllowed(state: ChatMemoryState): Boolean {
        val rule = settings.rule(state.chatId)
        return collectionAllowed(state.chatId, rule, state.chatId in groupChatIds)
    }

    private fun collectionAllowed(chatId: String, rule: ChatRule, group: Boolean): Boolean =
        chatId in listedChatIds && rule.useMemory &&
            (
                settings.get().memoryCollectionScope == MemoryCollectionScope.ALL_ALLOWED ||
                    rule.enabled && (!group || groupEnabled(rule))
                )

    private fun allowed(state: ChatMemoryState): Boolean {
        if (automaticallyAllowed(state)) {
            return true
        }
        val explicit = state.manual && collections.stream().anyMatch { it.chatId == state.chatId }
        val requested = explicit && (state.requested || state.deltaTargetId != 0L || !state.backfillComplete)
        return state.chatId in listedChatIds && settings.rule(state.chatId).useMemory && requested
    }

    private fun groupEnabled(rule: ChatRule): Boolean = (
        if (rule.groupReplies == GroupReplyMode.INHERIT) {
            settings.get().groupReplies
        } else {
            rule.groupReplies
        }
        ) !=
        GroupReplyMode.DISABLED

    override fun close() {
        synchronized(executor) {
            if (closed) {
                return
            }
            closed = true
            executor.shutdown()
        }
        val finished = completion.get()
        val id = outstanding.get()
        if (id.isNotEmpty()) {
            queue.cancelMemory(id)
        }
        inference.interruptBackground()
        try {
            if (finished != null && !finished.await(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("Shared memory worker did not stop within the shutdown timeout")
            }
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println("Conversation memory worker did not stop within the shutdown timeout")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            System.err.println("Conversation memory shutdown was interrupted")
        }
    }
}
