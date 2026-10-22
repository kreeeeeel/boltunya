package ru.aigram.storage

import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.history.ArchiveBucketSnapshot
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import ru.aigram.validation.InputRule
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Clock
import java.util.stream.Collectors

private const val MESSAGE_ID_SHIFT = 20
private const val MESSAGES_PER_BUCKET = 128
private const val MAX_CACHED_BUCKETS = 64
private const val MAX_CACHED_BUCKET_BYTES = 128L * 1024

@Component
class MessageArchiveStore(directory: Path, private val clock: Clock) {
    private val root = directory.resolve("history")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutationVersions = mutableMapOf<Path, Long>()
    private val cachedBuckets = linkedMapOf<Path, ArchiveBucketSnapshot>()

    @Synchronized
    fun version(account: String, chat: String): Long {
        if (account.isEmpty()) {
            return 0
        }
        return mutationVersions[folder(account, chat)] ?: 0
    }

    @Synchronized
    fun recordEdited(account: String, messages: List<ChatMessage>) {
        if (account.isEmpty() || messages.isEmpty()) {
            return
        }
        messages.stream().map { it.chatId }.distinct().forEach { chat ->
            val key = folder(account, chat)
            mutationVersions[key] = (mutationVersions[key] ?: 0) + 1
        }
        record(account, messages)
    }

    @Synchronized
    fun recordPage(
        account: String,
        chat: String,
        messages: List<ChatMessage>,
        capturedVersion: Long,
    ): List<ChatMessage> {
        if (account.isEmpty() || messages.isEmpty()) {
            return messages
        }
        if (messages.stream().anyMatch { it.chatId != chat }) {
            throw InvalidHistoryIdentifierException()
        }

        val folder = folder(account, chat)
        if (version(account, chat) == capturedVersion) {
            record(account, messages)
        }
        // A changed epoch makes the whole native snapshot stale, including same-second edits.
        // Unknown entries are returned for this page but only a fresh read may persist them.
        val retained = messages.stream().map { bucket(it.id) }.distinct()
            .flatMap { read(folder.resolve("$it.json")).values.stream() }
            .collect(Collectors.toMap({ it.id }, { it }))
        val deleted = read(folder.resolve("deleted.json"))
        return messages.stream().map { message -> deleted[message.id] ?: retained[message.id] ?: message }.toList()
    }

    @Synchronized
    fun record(account: String, messages: List<ChatMessage>) {
        if (account.isEmpty() || messages.isEmpty()) {
            return
        }
        messages.groupBy { it.chatId }.entries.stream().forEach { (chat, items) ->
            val folder = folder(account, chat)
            val deleted = read(folder.resolve("deleted.json"))
            var changedDeleted = false
            items.groupBy { bucket(it.id) }.entries.stream().forEach { (bucket, batch) ->
                val path = folder.resolve("$bucket.json")
                val previous = read(path)
                val next = previous.toMutableMap()
                batch.stream().forEach { message ->
                    val tombstone = deleted[message.id]
                    val archived = previous[message.id]
                    val retained = if (tombstone != null) {
                        if (tombstone.date > 0) {
                            tombstone
                        } else {
                            message.copy(deleted = true, deletedAt = tombstone.deletedAt)
                        }
                    } else if (archived != null && archived.editedAt > message.editedAt) {
                        archived
                    } else {
                        message
                    }
                    next[message.id] = retained
                    if (tombstone != null && tombstone.date == 0L) {
                        deleted[message.id] = retained
                        changedDeleted = true
                    }
                }
                if (next != previous) {
                    write(path, next)
                }
            }
            if (changedDeleted) {
                write(folder.resolve("deleted.json"), deleted)
            }
        }
    }

    @Synchronized
    fun delete(account: String, chat: String, ids: List<Long>, permanent: Boolean, fromCache: Boolean): Boolean {
        if (account.isEmpty() || !permanent || fromCache || ids.isEmpty()) {
            return false
        }
        val folder = folder(account, chat)
        val deleted = read(folder.resolve("deleted.json"))
        if (ids.stream().allMatch { deleted.containsKey(it.toString()) }) {
            return false
        }
        ids.groupBy { bucket(it.toString()) }.entries.stream().forEach { (bucket, batch) ->
            val path = folder.resolve("$bucket.json")
            val originals = read(path)
            batch.stream().forEach { id ->
                val key = id.toString()
                val original = originals[key] ?: ChatMessage(key, chat, "", false, 0)
                val message = deleted[key] ?: original.copy(deleted = true, deletedAt = clock.instant().epochSecond)
                deleted[key] = message
                if (key in originals) {
                    originals[key] = message
                }
            }
            if (originals.isNotEmpty()) {
                write(path, originals)
            }
        }
        write(folder.resolve("deleted.json"), deleted)
        return true
    }

    @Synchronized
    fun find(account: String, chat: String, id: String): ChatMessage? {
        if (account.isEmpty()) {
            return null
        }
        return read(folder(account, chat).resolve("${bucket(id)}.json"))[id]
    }

    @Synchronized
    fun deleted(account: String, chat: String, before: Long = 0, limit: Int = 100): List<ChatMessage> {
        if (account.isEmpty()) {
            return emptyList()
        }
        return read(folder(account, chat).resolve("deleted.json")).values.stream()
            .filter { it.date > 0 && (before == 0L || it.id.toLong() < before) }
            .sorted(compareByDescending { it.id.toLong() }).limit(limit.toLong()).toList().asReversed()
    }

    fun merge(account: String, chat: String, current: List<ChatMessage>, before: Long, limit: Int): List<ChatMessage> =
        (current + deleted(account, chat, before, limit)).associateBy { it.id }.values.stream()
            .sorted(compareByDescending { it.id.toLong() }).limit(limit.toLong()).toList().asReversed()

    private fun folder(account: String, chat: String): Path {
        listOf(account, chat).stream().forEach {
            if (!InputRule.CHAT_ID.accepts(it)) {
                throw InvalidHistoryIdentifierException()
            }
        }
        return root.resolve(account).resolve(chat)
    }

    private fun bucket(id: String): Long {
        val number = id.toLongOrNull()
        if (number == null || number <= 0) {
            throw InvalidHistoryIdentifierException()
        }
        return (number shr MESSAGE_ID_SHIFT) / MESSAGES_PER_BUCKET
    }

    private fun read(path: Path): MutableMap<String, ChatMessage> {
        val attributes = try {
            Files.readAttributes(path, BasicFileAttributes::class.java)
        } catch (exception: NoSuchFileException) {
            cachedBuckets.remove(path)
            return mutableMapOf()
        }

        val cached = cachedBuckets.remove(path)
        if (cached != null && cached.sizeBytes == attributes.size() &&
            cached.modifiedAt == attributes.lastModifiedTime() && cached.fileKey == attributes.fileKey()
        ) {
            cachedBuckets[path] = cached
            return cached.messages.toMutableMap()
        }

        val messages = json.decodeFromString<Map<String, ChatMessage>>(Files.readString(path))
        if (attributes.size() <= MAX_CACHED_BUCKET_BYTES) {
            cachedBuckets[path] = ArchiveBucketSnapshot(
                attributes.size(),
                attributes.lastModifiedTime(),
                attributes.fileKey(),
                messages,
            )
            if (cachedBuckets.size > MAX_CACHED_BUCKETS) {
                cachedBuckets.remove(cachedBuckets.keys.first())
            }
        }

        return messages.toMutableMap()
    }

    private fun write(path: Path, messages: Map<String, ChatMessage>) {
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temporary, json.encodeToString(messages))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        cachedBuckets.remove(path)
    }
}
