package ru.boltunya.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import ru.boltunya.domain.OutgoingMedia
import ru.boltunya.domain.OutgoingMediaKind
import ru.boltunya.exception.impl.InvalidUploadException

class TelegramMediaContent @Inject constructor() {
    fun content(media: OutgoingMedia): TdApi.InputMessageContent {
        val file = TdApi.InputFileLocal(media.path.toAbsolutePath().toString())
        val caption = TdApi.FormattedText(media.caption, emptyArray())
        return when (media.kind) {
            OutgoingMediaKind.MEDIA -> throw InvalidUploadException()
            OutgoingMediaKind.PHOTO -> TdApi.InputMessagePhoto().apply {
                photo = TdApi.InputPhoto().apply {
                    photo = file
                    addedStickerFileIds = intArrayOf()
                }
                this.caption = caption
            }
            OutgoingMediaKind.VIDEO -> TdApi.InputMessageVideo().apply {
                video =
                    TdApi.InputVideo().apply {
                        video = file
                        addedStickerFileIds = intArrayOf()
                        supportsStreaming =
                            true
                    }
                this.caption = caption
            }
            OutgoingMediaKind.DOCUMENT -> TdApi.InputMessageDocument().apply {
                document = TdApi.InputDocument().apply {
                    document = file
                    disableContentTypeDetection = true
                }
                this.caption = caption
            }
        }
    }

    fun request(chatId: Long, media: List<OutgoingMedia>, replyToId: String, topicId: Int): TdApi.Function<*> {
        if (media.size !in 1..10 || topicId < 0 || media.stream().anyMatch { it.caption.length > 1024 } ||
            media.stream().anyMatch { it.kind == OutgoingMediaKind.DOCUMENT } &&
            media.stream().anyMatch { it.kind != OutgoingMediaKind.DOCUMENT }
        ) {
            throw InvalidUploadException()
        }
        val reply = if (replyToId.isEmpty()) {
            null
        } else {
            TdApi.InputMessageReplyToMessage(
                replyToId.toLongOrNull()?.takeIf { it > 0 } ?: throw InvalidUploadException(),
                null,
                0,
                "",
            )
        }
        val topic = if (topicId == 0) null else TdApi.MessageTopicForum(topicId)
        return if (media.size == 1) {
            TdApi.SendMessage().apply {
                this.chatId = chatId
                this.topicId = topic
                replyTo = reply
                inputMessageContent = content(media[0])
            }
        } else {
            TdApi.SendMessageAlbum().apply {
                this.chatId = chatId
                this.topicId = topic
                replyTo = reply
                inputMessageContents = media.stream().map(::content).toList().toTypedArray()
            }
        }
    }
}
