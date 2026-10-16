package ru.boltunya.memory

import ru.boltunya.automation.FakeTelegramGateway
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ChatSummary
import ru.boltunya.domain.TelegramStatus
import ru.boltunya.telegram.TelegramGateway

class MemoryTelegramGateway : TelegramGateway by FakeTelegramGateway() {
    var ownerId = "100"
    var messages: List<ChatMessage> = emptyList()
    var chatIds: List<String> = listOf("42", "43")
    var groupChats: Set<String> = emptySet()
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
            kind = if (id in
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
        return messages.filter { it.chatId == chatId && (before == 0L || it.id.toLong() < before) }.takeLast(limit)
    }
}
