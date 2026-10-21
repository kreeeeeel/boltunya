package ru.aigram.automation

import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.domain.telegram.TelegramCredentials
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.telegram.TelegramGateway
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Stream

class FakeTelegramGateway : TelegramGateway {
    val sent = CopyOnWriteArrayList<String>()
    val replyIds = CopyOnWriteArrayList<String>()
    val topicIds = CopyOnWriteArrayList<Int>()
    val entities = CopyOnWriteArrayList<List<MessageEntity>>()
    val sentAutomated = CopyOnWriteArrayList<Boolean>()
    val typing = CopyOnWriteArrayList<Boolean>()
    val sendTimes = CopyOnWriteArrayList<Long>()
    var nextSendError: RuntimeException? = null
    var beforeSend: () -> Unit = {}
    var messages = listOf(ChatMessage("1", "42", "Привет", false, 0))
    var chatSummaries = Stream.of("42", "43").map { ChatSummary(it, "Test", "", 0, 0, ChatRule()) }.toList()

    override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = "100")
    override fun connect(credentials: TelegramCredentials) = Unit
    override fun restore() = Unit
    override fun submit(value: String) = Unit
    override fun chats(): List<ChatSummary> = chatSummaries
    override fun history(chatId: String, limit: Int, before: Long): List<ChatMessage> =
        messages.filter { it.chatId == chatId }.takeLast(limit)
    override fun send(
        chatId: String,
        text: String,
        replyToId: String,
        topicId: Int,
        entities: List<MessageEntity>,
        automated: Boolean,
    ): String {
        beforeSend()
        val failure = nextSendError
        nextSendError = null
        if (failure != null) {
            throw failure
        }
        sent.add(text)
        replyIds.add(replyToId)
        topicIds.add(topicId)
        this.entities.add(entities)
        sentAutomated.add(automated)
        sendTimes.add(System.nanoTime())
        return "sent-${sent.size}"
    }
    override fun typing(chatId: String, active: Boolean) {
        typing.add(active)
    }
    override fun logout() = Unit
    override fun close() = Unit
}
