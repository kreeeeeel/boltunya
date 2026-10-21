package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.MessageEditKind
import ru.aigram.domain.chat.MessageEditOptions
import ru.aigram.domain.chat.MessageEditRequest
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import ru.aigram.exception.impl.InvalidMessageEditException
import ru.aigram.exception.impl.MessageEditConflictException
import ru.aigram.exception.impl.MessageEditNotAllowedException
import ru.aigram.exception.impl.MessageEditTooLongException
import ru.aigram.exception.impl.TelegramEditFailedException
import java.io.DataOutputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.HexFormat

@Component
class TelegramMessageEditPolicy(private val entities: TelegramMessageEditEntities) {
    fun messageId(value: String): Long {
        val id = value.toLongOrNull()
        if (id == null || id <= 0 || id.toString() != value) {
            throw InvalidHistoryIdentifierException()
        }
        return id
    }

    fun verifyOwner(message: TdApi.Message, properties: TdApi.MessageProperties, owner: String) {
        val sender = message.senderId
        if (!message.isOutgoing || !properties.canBeEdited || message.sendingState != null || sender == null ||
            sender is TdApi.MessageSenderUser && sender.userId.toString() != owner
        ) {
            throw MessageEditNotAllowedException()
        }
        formatted(message)
    }

    fun verifyConnection(owner: String, version: Long, actualOwner: String, actualVersion: Long, sameClient: Boolean) {
        if (owner.isEmpty() || owner == "0" || actualOwner != owner || actualVersion != version || !sameClient) {
            throw MessageEditConflictException()
        }
    }

    fun options(message: TdApi.Message, owner: String, version: Long, maximum: Int): MessageEditOptions {
        val text = formatted(message)
        return MessageEditOptions(
            text.text,
            entities.describe(text),
            revision(message, owner, version),
            kind(message),
            maximum,
        )
    }

    fun kind(message: TdApi.Message): MessageEditKind = when (message.content) {
        is TdApi.MessageText -> MessageEditKind.TEXT
        is TdApi.MessagePhoto, is TdApi.MessageVideo -> MessageEditKind.CAPTION
        else -> throw MessageEditNotAllowedException()
    }

    fun formatted(message: TdApi.Message): TdApi.FormattedText = when (val content = message.content) {
        is TdApi.MessageText -> content.text
        is TdApi.MessagePhoto -> content.caption
        is TdApi.MessageVideo -> content.caption
        else -> throw MessageEditNotAllowedException()
    }

    fun limit(option: TdApi.OptionValue): Int {
        if (option !is TdApi.OptionValueInteger || option.value <= 0 || option.value > Int.MAX_VALUE) {
            throw TelegramEditFailedException()
        }
        return option.value.toInt()
    }

    fun updated(message: TdApi.Message, request: MessageEditRequest, maximum: Int): TdApi.FormattedText {
        if (request.text.isBlank() && kind(message) == MessageEditKind.TEXT) {
            throw InvalidMessageEditException()
        }
        if (request.text.codePointCount(0, request.text.length) > maximum) {
            throw MessageEditTooLongException(maximum)
        }
        return entities.format(request.text, request.entities, formatted(message))
    }

    fun request(message: TdApi.Message, text: TdApi.FormattedText): TdApi.Function<TdApi.Message> =
        when (val content = message.content) {
            is TdApi.MessageText -> TdApi.EditMessageText(
                message.chatId,
                message.id,
                null,
                TdApi.InputMessageText(text, content.linkPreviewOptions, false),
            )
            is TdApi.MessagePhoto ->
                TdApi.EditMessageCaption(message.chatId, message.id, null, text, content.showCaptionAboveMedia)
            is TdApi.MessageVideo ->
                TdApi.EditMessageCaption(message.chatId, message.id, null, text, content.showCaptionAboveMedia)
            else -> throw MessageEditNotAllowedException()
        }

    fun revision(message: TdApi.Message, owner: String, version: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DataOutputStream(DigestOutputStream(OutputStream.nullOutputStream(), digest)).use { output ->
            output.writeUTF(owner)
            output.writeLong(version)
            output.writeLong(message.chatId)
            output.writeLong(message.id)
            output.writeInt(message.editDate)
            output.writeInt(message.content.constructor)
            formatted(message).serialize(output)
            when (val content = message.content) {
                is TdApi.MessageText -> {
                    output.writeBoolean(content.linkPreviewOptions != null)
                    content.linkPreviewOptions?.serialize(output)
                }
                is TdApi.MessagePhoto -> {
                    output.writeBoolean(content.showCaptionAboveMedia)
                    output.writeBoolean(content.hasSpoiler)
                    output.writeBoolean(content.isSecret)
                    photoIdentity(content.photo, output)
                    fileIdentity(content.video?.video, output)
                }
                is TdApi.MessageVideo -> {
                    output.writeBoolean(content.showCaptionAboveMedia)
                    output.writeBoolean(content.hasSpoiler)
                    output.writeBoolean(content.isSecret)
                    fileIdentity(content.video.video, output)
                    output.writeBoolean(content.cover != null)
                    content.cover?.let { photoIdentity(it, output) }
                    output.writeInt(content.startTimestamp)
                }
                else -> throw MessageEditNotAllowedException()
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun photoIdentity(photo: TdApi.Photo, output: DataOutputStream) {
        output.writeInt(photo.sizes.orEmpty().size)
        photo.sizes.orEmpty().asList().stream().sorted(compareBy { it.type }).forEach { size ->
            output.writeUTF(size.type)
            fileIdentity(size.photo, output)
        }
    }

    private fun fileIdentity(file: TdApi.File?, output: DataOutputStream) {
        output.writeInt(file?.id ?: 0)
        output.writeUTF(file?.remote?.uniqueId.orEmpty())
    }
}
