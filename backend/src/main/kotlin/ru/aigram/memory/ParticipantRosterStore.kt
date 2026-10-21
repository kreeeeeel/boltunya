package ru.aigram.memory

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.memory.ParticipantIdentity
import ru.aigram.domain.memory.ParticipantRecord
import ru.aigram.exception.impl.InvalidMemoryChatException
import ru.aigram.exception.impl.MemoryTooLargeException
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.validation.InputRule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.LinkedHashMap
import java.util.stream.Collectors
import java.util.stream.Stream

private const val MAX_PARTICIPANTS = 1000
private const val MAX_CONTEXT_PARTICIPANTS = 80L
private const val MAX_CACHED_ROSTERS = 64
private const val MAX_ROSTER_BYTES = 1024L * 1024
private const val MAX_PARTICIPANT_NAME = 128
private const val MAX_USERNAME_LENGTH = 64
private const val MAX_CONTEXT_CHARACTERS = 4000

/** A deterministic index of observed group authors. It never fetches a group's member list or infers traits. */
@Component
class ParticipantRosterStore(directory: Path, private val exclusions: ContextExclusionStore? = null) {
    private val root = directory.resolve("memory")
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = LinkedHashMap<Path, List<ParticipantRecord>>(MAX_CACHED_ROSTERS, 0.75f, true)

    @Synchronized
    fun record(ownerId: String, chatId: String, messages: List<ChatMessage>) {
        val path = file(ownerId, chatId)
        val candidates = messages.stream().filter {
            it.chatId == chatId && it.group && !it.contextExcluded && !it.deleted
        }.map(::participant).flatMap {
            if (it == null) {
                Stream.empty()
            } else {
                Stream.of(it)
            }
        }.toList()
        if (candidates.isEmpty()) {
            return
        }
        val previous = read(path)
        val next = (previous + candidates).stream().filter {
            exclusions?.excludes(ownerId, chatId, it.lastMessageId.toString()) != true
        }.collect(Collectors.toMap({ it.id }, { it }, ::newer))
            .values.stream().sorted(
                compareByDescending<ParticipantRecord> { it.lastSeenDate }
                    .thenByDescending { it.lastMessageId }.thenBy { it.id },
            ).limit(MAX_PARTICIPANTS.toLong()).toList()
        if (next == previous) {
            return
        }
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling("PARTICIPANTS.json.tmp")
        Files.writeString(temporary, json.encodeToString(next))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        remember(path, next)
    }

    @Synchronized
    fun context(ownerId: String, chatId: String): List<ParticipantIdentity> {
        var characters = 2
        return read(file(ownerId, chatId)).stream().filter {
            exclusions?.excludes(ownerId, chatId, it.lastMessageId.toString()) != true
        }.limit(MAX_CONTEXT_PARTICIPANTS).map { ParticipantIdentity(it.id, it.name, it.username) }.takeWhile {
            characters += json.encodeToString(it).length + 1
            characters <= MAX_CONTEXT_CHARACTERS
        }.toList()
    }

    private fun read(path: Path): List<ParticipantRecord> {
        val existing = cache[path]
        if (existing != null) {
            return existing
        }
        if (Files.exists(path) && Files.size(path) > MAX_ROSTER_BYTES) {
            throw MemoryTooLargeException()
        }
        val records = if (Files.exists(
                path,
            )
        ) {
            json.decodeFromString<List<ParticipantRecord>>(Files.readString(path))
        } else {
            emptyList()
        }
        if (records.size > MAX_PARTICIPANTS) {
            throw MemoryTooLargeException()
        }
        remember(path, records)
        return records
    }

    private fun remember(path: Path, records: List<ParticipantRecord>) {
        cache[path] = records
        if (cache.size > MAX_CACHED_ROSTERS) {
            cache.entries.stream().limit((cache.size - MAX_CACHED_ROSTERS).toLong())
                .map { it.key }.toList().stream().forEach(cache::remove)
        }
    }

    private fun participant(message: ChatMessage): ParticipantRecord? {
        val id = message.senderId
        val rawId = id.substringAfter(':', "")
        if (!InputRule.CHAT_ID.accepts(rawId) ||
            !id.startsWith("chat:") && (!id.startsWith("user:") || (rawId.toLongOrNull() ?: 0) <= 0)
        ) {
            return null
        }
        val messageId = message.id.toLongOrNull()?.takeIf { it > 0 }

        if (messageId == null) {
            return null
        }

        val name = message.senderName.codePoints().filter { !Character.isISOControl(it) }
            .limit(MAX_PARTICIPANT_NAME.toLong()).mapToObj { String(Character.toChars(it)) }
            .collect(Collectors.joining()).trim()
        val username = message.senderUsername.removePrefix("@").takeIf { value ->
            value.length <= MAX_USERNAME_LENGTH &&
                value.chars().allMatch {
                    it in 'a'.code..'z'.code || it in 'A'.code..'Z'.code ||
                        it in '0'.code..'9'.code ||
                        it == '_'.code
                }
        }.orEmpty()
        if (name.isEmpty() && username.isEmpty()) {
            return null
        }
        return ParticipantRecord(id, name, username, message.date, messageId)
    }

    private fun newer(first: ParticipantRecord, second: ParticipantRecord): ParticipantRecord =
        if (compareValuesBy(first, second, { it.lastSeenDate }, { it.lastMessageId }) > 0) {
            first
        } else {
            second
        }

    private fun file(ownerId: String, chatId: String): Path {
        if (!InputRule.CHAT_ID.accepts(ownerId) || (ownerId.toLongOrNull() ?: 0) <= 0 ||
            !InputRule.CHAT_ID.accepts(chatId)
        ) {
            throw InvalidMemoryChatException()
        }
        return root.resolve(ownerId).resolve(chatId).resolve("PARTICIPANTS.json")
    }
}
