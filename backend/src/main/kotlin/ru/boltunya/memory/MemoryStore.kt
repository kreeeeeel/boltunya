package ru.boltunya.memory

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import ru.boltunya.exception.impl.InvalidMemoryChatException
import ru.boltunya.exception.impl.MemoryConflictException
import ru.boltunya.exception.impl.MemoryNotReadyException
import ru.boltunya.exception.impl.MemoryTooLargeException
import ru.boltunya.resources.ResourceCatalog
import ru.boltunya.validation.InputRule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat

private const val SUMMARY_START = "<!-- boltunya:auto:start -->"
private const val SUMMARY_END = "<!-- boltunya:auto:end -->"
private const val FACTS_MARKER = "<!-- boltunya:facts:$FACTUAL_MEMORY_VERSION -->"
private const val MAX_NOTES_CHARACTERS = 16000
private const val MAX_SUMMARY_CHARACTERS = 6000
private const val MAX_DOCUMENT_BYTES = 256L * 1024

class MemoryStore @Inject constructor(directory: Path, private val resources: ResourceCatalog) {
    private val root = directory.resolve("memory")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private var accountId = ""
    private var states: Map<String, ChatMemoryState> = emptyMap()

    @Synchronized
    fun activate(ownerId: String) {
        if (!InputRule.CHAT_ID.accepts(ownerId)) {
            throw MemoryNotReadyException()
        }
        if (accountId == ownerId) return
        val index = root.resolve(ownerId).resolve("state.json")
        val loaded = if (Files.exists(
                index,
            )
        ) {
            json.decodeFromString<Map<String, ChatMemoryState>>(Files.readString(index))
        } else {
            emptyMap()
        }
        val profile = root.resolve(ownerId).resolve("PROFILE.md")
        if (Files.exists(profile) && Files.size(profile) > MAX_DOCUMENT_BYTES) throw MemoryTooLargeException()
        val notes = read("").notes
        val ownerSummary = if (Files.exists(profile)) Files.readString(profile).trim() else ""
        write(file(""), markdown(notes, content(ownerSummary), version(ownerSummary)))
        accountId = ownerId
        states = loaded
    }

    @Synchronized
    fun initialize() {
        if (!Files.exists(file(""))) write(file(""), defaultNotes(""))
    }

    @Synchronized
    fun states(): List<ChatMemoryState> = states.values.toList()

    @Synchronized
    fun state(chatId: String): ChatMemoryState? = states[chatId]

    @Synchronized
    fun read(chatId: String): StoredMemory {
        val path = file(chatId)
        if (Files.exists(path) && Files.size(path) > MAX_DOCUMENT_BYTES) throw MemoryTooLargeException()
        val text = if (Files.exists(path)) Files.readString(path) else defaultNotes(chatId)
        val start = text.indexOf(SUMMARY_START)
        val end = text.indexOf(SUMMARY_END)
        val hasSummary = start >= 0 && end > start
        val notes = if (hasSummary) {
            val prefix = text.substring(0, start).removeSuffix("\n\n")
            val suffix = text.substring(end + SUMMARY_END.length).removePrefix("\n")
            if (suffix.isEmpty()) prefix else prefix + "\n\n" + suffix
        } else if (chatId.isEmpty()) {
            text.removeSuffix("\n")
        } else {
            text
        }
        val summary = if (hasSummary) text.substring(start + SUMMARY_START.length, end).trim() else ""
        return StoredMemory(notes, content(summary), digest(notes), digest(text), version(summary))
    }

    fun hasNotes(chatId: String, notes: String): Boolean =
        notes.isNotBlank() && notes.trim() != defaultNotes(chatId).trim()

    @Synchronized
    fun relativePath(chatId: String): String {
        file(chatId)
        return if (chatId.isEmpty()) "memory/MEMORY.md" else "memory/$accountId/$chatId/MEMORY.md"
    }

    @Synchronized
    fun edit(chatId: String, notes: String, revision: String) {
        if (notes.length > MAX_NOTES_CHARACTERS || notes.contains(SUMMARY_START) || notes.contains(SUMMARY_END)) {
            throw MemoryTooLargeException()
        }
        val previous = read(chatId)
        if (previous.revision != revision) throw MemoryConflictException()
        write(file(chatId), markdown(notes, previous.summary, previous.factsVersion))
    }

    @Synchronized
    fun observe(
        chatId: String,
        messageId: Long = 0,
        manual: Boolean = false,
        request: Boolean = false,
        ownerSource: Boolean = false,
    ) {
        file(chatId)
        val previous = states[chatId] ?: ChatMemoryState(chatId, accountId = accountId)
        if (chatId !in states &&
            !Files.exists(file(chatId))
        ) {
            write(file(chatId), markdown(defaultNotes(chatId), ""))
        }
        val migrateProfile = previous.ownerMemoryVersion == 0 && previous.headId != 0L
        val migrateFacts = previous.factsVersion < FACTUAL_MEMORY_VERSION &&
            (previous.headId != 0L || previous.backfillBeforeId != 0L || previous.backfillComplete)
        if (messageId <= previous.observedId && (!manual || previous.manual) &&
            (!ownerSource || previous.ownerSource) && !request && !migrateProfile && !migrateFacts && chatId in states
        ) {
            return
        }
        val changed = messageId > previous.observedId || request
        val next = previous.copy(
            observedId = maxOf(previous.observedId, messageId),
            dirty = previous.dirty || messageId > previous.headId,
            requested = previous.requested || request,
            manual = previous.manual || manual,
            ownerSource = previous.ownerSource || ownerSource,
            changeVersion = previous.changeVersion + if (changed) 1 else 0,
        ).let { next ->
            if (migrateProfile || migrateFacts) {
                next.copy(
                    headId = 0,
                    backfillBeforeId = 0,
                    backfillComplete = false,
                    deltaTargetId = 0,
                    deltaTargetDate = 0,
                    deltaBeforeId = 0,
                    dirty = true,
                    generation = previous.generation + 1,
                )
            } else {
                next
            }
        }
        if (request || manual && !previous.manual ||
            ownerSource && !previous.ownerSource || migrateProfile || migrateFacts || chatId !in states
        ) {
            persist(next)
        } else {
            states = states + (chatId to next)
        }
    }

    @Synchronized
    fun commit(
        previous: ChatMemoryState,
        revision: String,
        summary: String,
        next: ChatMemoryState,
        ownerUpdate: OwnerMemoryUpdate? = null,
    ): Boolean {
        if (summary.length > MAX_SUMMARY_CHARACTERS) throw MemoryTooLargeException()
        if (ownerUpdate != null && ownerUpdate.summary.length > MAX_SUMMARY_CHARACTERS) throw MemoryTooLargeException()
        val current = states[previous.chatId] ?: return false
        if (accountId != previous.accountId) return false
        val document = read(previous.chatId)
        if (accountId != previous.accountId || current.generation != previous.generation ||
            document.documentRevision != revision
        ) {
            return false
        }
        if (ownerUpdate != null) {
            val general = read("")
            if (general.documentRevision != ownerUpdate.previous.documentRevision) return false
            write(
                root.resolve(accountId).resolve("PROFILE.md"),
                FACTS_MARKER + "\n" + ownerUpdate.summary.trim() + "\n",
            )
            write(file(""), markdown(general.notes, ownerUpdate.summary))
        }
        write(file(previous.chatId), markdown(document.notes, summary))
        val changedWhileRunning = current.changeVersion != previous.changeVersion
        persist(
            next.copy(
                observedId = maxOf(current.observedId, next.observedId),
                dirty = next.dirty || current.observedId > next.headId,
                requested = next.requested || changedWhileRunning && current.requested,
                manual = current.manual,
                ownerSource = current.ownerSource,
                changeVersion = current.changeVersion,
                factsVersion = FACTUAL_MEMORY_VERSION,
            ),
        )
        return true
    }

    @Synchronized
    fun cancelCollection(chatId: String) {
        val previous = states[chatId] ?: return
        persist(previous.copy(requested = false, generation = previous.generation + 1))
    }

    @Synchronized
    fun rebuild(chatId: String, manual: Boolean = true) {
        val previous = states[chatId] ?: ChatMemoryState(chatId, accountId = accountId)
        val document = read(chatId)
        write(file(chatId), markdown(document.notes, ""))
        persist(
            ChatMemoryState(
                chatId,
                accountId = accountId,
                manual = previous.manual || manual,
                ownerSource = previous.ownerSource,
                requested = true,
                generation =
                previous.generation + 1,
            ),
        )
    }

    @Synchronized
    fun rebuildOwner() {
        if (accountId.isEmpty()) throw MemoryNotReadyException()
        val general = read("")
        write(root.resolve(accountId).resolve("PROFILE.md"), "")
        write(file(""), markdown(general.notes, ""))
    }

    private fun file(chatId: String): Path {
        if (chatId.isEmpty()) return root.resolve("MEMORY.md")
        if (!InputRule.CHAT_ID.accepts(chatId)) {
            throw InvalidMemoryChatException()
        }
        if (accountId.isEmpty()) throw MemoryNotReadyException()
        return root.resolve(accountId).resolve(chatId).resolve("MEMORY.md")
    }

    private fun defaultNotes(chatId: String): String = if (chatId.isEmpty()) {
        resources.prompt("ru/memory/global-file")
    } else {
        resources.prompt("ru/memory/chat-file", mapOf("chatId" to chatId))
    }

    private fun markdown(notes: String, summary: String, factsVersion: Int = FACTUAL_MEMORY_VERSION): String =
        notes + "\n\n" + SUMMARY_START + "\n" +
            (if (factsVersion == FACTUAL_MEMORY_VERSION) FACTS_MARKER + "\n" else "") +
            summary.trim() + "\n" + SUMMARY_END + "\n"

    private fun content(summary: String): String = summary.removePrefix(FACTS_MARKER).trim()

    private fun version(summary: String): Int = if (summary.startsWith(FACTS_MARKER)) FACTUAL_MEMORY_VERSION else 0

    private fun persist(state: ChatMemoryState) {
        val next = states + (state.chatId to state)
        write(root.resolve(accountId).resolve("state.json"), json.encodeToString(next))
        states = next
    }

    private fun write(path: Path, text: String) {
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temporary, text)
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun digest(text: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
}
