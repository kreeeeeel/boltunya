package ru.boltunya.http

import ru.boltunya.domain.OutgoingMedia
import ru.boltunya.domain.StickerCatalog
import ru.boltunya.memory.MemoryTelegramGateway
import ru.boltunya.telegram.TelegramGateway

class MediaActionsGateway(val delegate: MemoryTelegramGateway = MemoryTelegramGateway()) : TelegramGateway by delegate {
    var sent: List<OutgoingMedia> = emptyList()
    var replyTo = ""
    var topic = 0
    var stickerId = ""
    val textSent = mutableListOf<String>()
    override fun send(chatId: String, text: String, replyToId: String, topicId: Int): String {
        textSent.add(text)
        return "sent-text"
    }
    override fun sendMedia(chatId: String, media: List<OutgoingMedia>, replyToId: String, topicId: Int): List<String> {
        sent = media
        replyTo = replyToId
        topic = topicId
        return listOf("sent-media")
    }
    override fun stickers(): StickerCatalog = StickerCatalog(emptyList(), emptyList(), emptyList())
    override fun sendSticker(chatId: String, stickerId: String, replyToId: String, topicId: Int): String {
        this.stickerId = stickerId
        replyTo = replyToId
        topic = topicId
        return "sent-sticker"
    }
}
