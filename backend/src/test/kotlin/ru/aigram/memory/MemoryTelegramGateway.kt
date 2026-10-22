package ru.aigram.memory

import ru.aigram.automation.FakeTelegramGateway
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.telegram.TelegramGateway

class MemoryTelegramGateway : TelegramGateway by FakeTelegramGateway() {
    var ownerId = "100"
    var messages: List<ChatMessage> = emptyList()
    var chatIds: List<String> = listOf("42", "43")
    var groupChats: Set<String> = emptySet()
    var channelChats: Set<String> = emptySet()
    var afterRead: () -> Unit = {}
    val reads = mutableListOf<Pair<String, Long>>()
    override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = ownerId)
    override fun chats(): List<ChatSummary> = chatIds.map { id ->
        val last = messages.lastOrNull { it.chatId == id }
        ChatSummary(
            id,
            "Chat",
            "",
            last?.date ?: 0,
            0,
            ChatRule(),
            lastMessageId = last?.id.orEmpty(),
            kind = if (id in channelChats) {
                "channel"
            } else if (id in
                groupChats
            ) {
                "group"
            } else {
                "private"
            },
        )
    }
    override fun history(chatId: String, limit: Int, before: Long): List<ChatMessage> {
        reads.add(chatId to before)
        val page = messages.filter { it.chatId == chatId && (before == 0L || it.id.toLong() < before) }.takeLast(limit)
        afterRead()
        return page
    }
}
