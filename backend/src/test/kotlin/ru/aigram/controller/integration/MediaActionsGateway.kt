package ru.aigram.controller.integration

import ru.aigram.domain.chat.MessageEntity
import ru.aigram.domain.media.StickerCatalog
import ru.aigram.memory.MemoryTelegramGateway
import ru.aigram.telegram.TelegramGateway

class MediaActionsGateway(val delegate: MemoryTelegramGateway = MemoryTelegramGateway()) : TelegramGateway by delegate {
    var replyTo = ""
    var topic = 0
    var stickerId = ""
    val textSent = mutableListOf<String>()
    val textEntities = mutableListOf<List<MessageEntity>>()
    override fun send(
        chatId: String,
        text: String,
        replyToId: String,
        topicId: Int,
        entities: List<MessageEntity>,
        automated: Boolean,
    ): String {
        textSent.add(text)
        textEntities.add(entities)
        return "sent-text"
    }
    override fun stickers(): StickerCatalog = StickerCatalog(emptyList(), emptyList(), emptyList())
    override fun sendSticker(
        chatId: String,
        stickerId: String,
        replyToId: String,
        topicId: Int,
        automated: Boolean,
    ): String {
        this.stickerId = stickerId
        replyTo = replyToId
        topic = topicId
        return "sent-sticker"
    }
}
