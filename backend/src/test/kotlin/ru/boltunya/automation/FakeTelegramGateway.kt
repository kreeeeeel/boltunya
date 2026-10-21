package ru.boltunya.automation

import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ChatSummary
import ru.boltunya.domain.TelegramCredentials
import ru.boltunya.domain.TelegramStatus
import ru.boltunya.telegram.TelegramGateway
import java.util.concurrent.CopyOnWriteArrayList

class FakeTelegramGateway : TelegramGateway {
    val sent = CopyOnWriteArrayList<String>()
    val replyIds = CopyOnWriteArrayList<String>()
    val topicIds = CopyOnWriteArrayList<Int>()
    val typing = CopyOnWriteArrayList<Boolean>()
    val sendTimes = CopyOnWriteArrayList<Long>()
    var nextSendError: RuntimeException? = null
    var beforeSend: () -> Unit = {}
    var messages = listOf(ChatMessage("1", "42", "Привет", false, 0))

    override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = "100")
    override fun connect(credentials: TelegramCredentials) = Unit
    override fun restore() = Unit
    override fun submit(value: String) = Unit
    override fun chats(): List<ChatSummary> = listOf("42", "43").map { ChatSummary(it, "Test", "", 0, 0, ChatRule()) }
    override fun history(chatId: String, limit: Int, before: Long): List<ChatMessage> =
        messages.filter { it.chatId == chatId }.takeLast(limit)
    override fun send(chatId: String, text: String, replyToId: String, topicId: Int): String {
        beforeSend()
        val failure = nextSendError
        nextSendError = null
        if (failure != null) throw failure
        sent.add(text)
        replyIds.add(replyToId)
        topicIds.add(topicId)
        sendTimes.add(System.nanoTime())
        return "sent-${sent.size}"
    }
    override fun typing(chatId: String, active: Boolean) {
        typing.add(active)
    }
    override fun logout() = Unit
    override fun close() = Unit
}
