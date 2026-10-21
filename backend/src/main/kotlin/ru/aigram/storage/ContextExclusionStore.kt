package ru.aigram.storage

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import ru.aigram.domain.ChatMessage
import ru.aigram.domain.ContextExclusionScope
import ru.aigram.domain.ContextExclusionUpdate
import ru.aigram.exception.impl.InvalidContextSelectionException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

private const val MAX_MESSAGE_OVERRIDES = 10000

/** Account-scoped context policy; Telegram messages themselves are never changed or deleted. */
@Component
class ContextExclusionStore @Inject constructor(directory: Path) {
    private val root = directory.resolve("context-exclusions")
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, ContextExclusionRules>()

    fun decorate(ownerId: String, messages: List<ChatMessage>): List<ChatMessage> = messages.map { message ->
        message.copy(contextExcluded = rules(ownerId, message.chatId).excludes(message.id))
    }

    fun forInference(ownerId: String, messages: List<ChatMessage>): List<ChatMessage> =
        decorate(ownerId, messages).filterNot { it.contextExcluded || it.deleted }.map { message ->
            if (message.replyToId.isNotBlank() && (
                    rules(ownerId, message.chatId).excludes(message.replyToId) ||
                        messages.stream().anyMatch {
                            it.chatId == message.chatId && it.id == message.replyToId &&
                                it.deleted
                        }
                    )
            ) {
                message.copy(replyText = "")
            } else {
                message
            }
        }

    fun revision(ownerId: String, chatId: String): Long = rules(ownerId, chatId).revision

    fun excludes(ownerId: String, chatId: String, messageId: String): Boolean =
        rules(ownerId, chatId).excludes(messageId)

    @Synchronized
    fun update(ownerId: String, chatId: String, messageId: String, update: ContextExclusionUpdate) {
        val id = messageId.toLongOrNull()
        if (id == null || id <= 0 || messageId != id.toString()) {
            throw InvalidContextSelectionException()
        }
        val previous = rules(ownerId, chatId)
        val entries = previous.entries.filterNot { it.messageId == id && it.scope == update.scope } +
            ContextExclusionRule(id, update.scope, update.excluded)
        if (entries.size > MAX_MESSAGE_OVERRIDES) throw InvalidContextSelectionException()
        persist(
            ownerId,
            chatId,
            previous.copy(
                entries = entries,
                revision = previous.revision + 1,
            ),
        )
    }

    @Synchronized
    fun clear(ownerId: String, chatId: String) {
        persist(ownerId, chatId, ContextExclusionRules(revision = rules(ownerId, chatId).revision + 1))
    }

    private fun rules(ownerId: String, chatId: String): ContextExclusionRules =
        cache.computeIfAbsent("$ownerId:$chatId") {
            val path = file(ownerId, chatId)
            if (Files.exists(path)) json.decodeFromString(Files.readString(path)) else ContextExclusionRules()
        }

    private fun persist(ownerId: String, chatId: String, rules: ContextExclusionRules) {
        val file = file(ownerId, chatId)
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(temporary, json.encodeToString(rules))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        cache["$ownerId:$chatId"] = rules
    }

    private fun file(ownerId: String, chatId: String): Path {
        val owner = ownerId.toLongOrNull()
        val chat = chatId.toLongOrNull()
        if (owner == null || owner <= 0 || ownerId != owner.toString() || chat == null || chat == 0L ||
            chatId != chat.toString()
        ) {
            throw InvalidContextSelectionException()
        }
        return root.resolve(ownerId).resolve("$chatId.json")
    }
}
