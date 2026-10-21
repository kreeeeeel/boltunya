package ru.aigram.speech.reply

import ru.aigram.domain.speech.OutgoingVoiceNote
import ru.aigram.telegram.TelegramGateway
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BlockingVoiceTestGateway(private val delegate: VoiceTestGateway = VoiceTestGateway()) :
    TelegramGateway by delegate {
    val started = CountDownLatch(1)
    val finish = CountDownLatch(1)

    override fun sendVoice(
        chatId: String,
        voice: OutgoingVoiceNote,
        replyToId: String,
        topicId: Int,
        automated: Boolean,
    ): String {
        started.countDown()
        finish.await(5, TimeUnit.SECONDS)
        return delegate.sendVoice(chatId, voice, replyToId, topicId, automated)
    }
}
