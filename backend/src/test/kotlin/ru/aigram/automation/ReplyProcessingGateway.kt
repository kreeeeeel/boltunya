package ru.aigram.automation

import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.telegram.TelegramGateway
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

class ReplyProcessingGateway(val delegate: FakeTelegramGateway = FakeTelegramGateway()) : TelegramGateway by delegate {
    @Volatile var owner = "100"
    val historyBefore = CopyOnWriteArrayList<Long>()
    val typingCancelled = CountDownLatch(1)

    override fun status(): TelegramStatus = TelegramStatus(state = "ready", userId = owner)

    override fun history(chatId: String, limit: Int, before: Long): List<ChatMessage> {
        historyBefore.add(before)
        // Deliberately includes future messages to verify the coordinator's independent anchor boundary.
        return delegate.history(chatId, limit, before)
    }

    override fun typing(chatId: String, active: Boolean) {
        delegate.typing(chatId, active)
        if (!active) {
            typingCancelled.countDown()
        }
    }
}
