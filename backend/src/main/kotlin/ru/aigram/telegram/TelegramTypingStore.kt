package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import org.springframework.stereotype.Component
import ru.aigram.domain.ChatTypingUser
import ru.aigram.domain.TypingAction
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

private const val TYPING_LIFETIME_MS = 6_000L

@Component
class TelegramTypingStore @Inject constructor(private val cache: TelegramCache, private val clock: Clock) {
    private val chats = ConcurrentHashMap<Long, MutableMap<String, ChatTypingState>>()
    private var ownerId = 0L

    @Synchronized
    fun update(chatId: Long, sender: TdApi.MessageSender, action: TdApi.ChatAction) {
        activate()
        if (cache.ownId == 0L || sender is TdApi.MessageSenderUser && sender.userId == cache.ownId) return
        val id = id(sender)
        if (id.isEmpty()) return
        val type = type(action)
        if (type == null) {
            clear(chatId, sender)
            return
        }
        val name = when (sender) {
            is TdApi.MessageSenderUser -> cache.users[sender.userId]?.let {
                (
                    it.firstName.orEmpty() + " " +
                        it.lastName.orEmpty()
                    ).trim()
            }.orEmpty()
            is TdApi.MessageSenderChat -> cache.chats[sender.chatId]?.title.orEmpty()
            else -> ""
        }
        val now = clock.millis()
        chats.values.stream().forEach { entries -> entries.entries.removeIf { it.value.expiresAt <= now } }
        chats.entries.removeIf { it.value.isEmpty() }
        chats.computeIfAbsent(chatId) { mutableMapOf() }[id] =
            ChatTypingState(ChatTypingUser(id, name, type), now + TYPING_LIFETIME_MS)
    }

    @Synchronized
    fun list(chatId: Long): List<ChatTypingUser> {
        activate()
        val entries = chats[chatId] ?: return emptyList()
        val now = clock.millis()
        entries.entries.removeIf { it.value.expiresAt <= now }
        if (entries.isEmpty()) chats.remove(chatId)
        return entries.values.stream().map { it.user }.sorted(compareBy { it.id }).toList()
    }

    @Synchronized
    fun clear(chatId: Long, sender: TdApi.MessageSender?) {
        activate()
        if (sender != null) chats[chatId]?.remove(id(sender))
    }

    private fun activate() {
        if (ownerId != cache.ownId) {
            chats.clear()
            ownerId = cache.ownId
        }
    }

    private fun id(sender: TdApi.MessageSender): String = when (sender) {
        is TdApi.MessageSenderUser -> "user:${sender.userId}"
        is TdApi.MessageSenderChat -> "chat:${sender.chatId}"
        else -> ""
    }

    private fun type(action: TdApi.ChatAction): TypingAction? = when (action) {
        is TdApi.ChatActionTyping -> TypingAction.TYPING
        is TdApi.ChatActionUploadingPhoto -> TypingAction.UPLOAD_PHOTO
        is TdApi.ChatActionUploadingVideo -> TypingAction.UPLOAD_VIDEO
        is TdApi.ChatActionUploadingDocument -> TypingAction.UPLOAD_DOCUMENT
        is TdApi.ChatActionRecordingVoiceNote -> TypingAction.RECORD_VOICE
        is TdApi.ChatActionUploadingVoiceNote -> TypingAction.UPLOAD_VOICE
        is TdApi.ChatActionRecordingVideo, is TdApi.ChatActionRecordingVideoNote -> TypingAction.RECORD_VIDEO
        is TdApi.ChatActionUploadingVideoNote -> TypingAction.UPLOAD_VIDEO_NOTE
        is TdApi.ChatActionChoosingSticker -> TypingAction.CHOOSE_STICKER
        else -> null
    }
}
