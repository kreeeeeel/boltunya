package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import org.springframework.stereotype.Component
import ru.aigram.domain.MessageEntity

@Component
class TelegramEntityMapper @Inject constructor() {
    fun map(text: TdApi.FormattedText): List<MessageEntity> = text.entities.orEmpty().mapNotNull { entity ->
        val type = entity.type
        val name = when (type) {
            is TdApi.TextEntityTypeBold -> "bold"
            is TdApi.TextEntityTypeItalic -> "italic"
            is TdApi.TextEntityTypeUnderline -> "underline"
            is TdApi.TextEntityTypeStrikethrough -> "strike"
            is TdApi.TextEntityTypeCode -> "code"
            is TdApi.TextEntityTypePre -> "pre"
            is TdApi.TextEntityTypePreCode -> "precode"
            is TdApi.TextEntityTypeUrl -> "url"
            is TdApi.TextEntityTypeTextUrl -> "texturl"
            is TdApi.TextEntityTypeEmailAddress -> "email"
            is TdApi.TextEntityTypePhoneNumber -> "phone"
            is TdApi.TextEntityTypeMention -> "mention"
            is TdApi.TextEntityTypeMentionName -> "mentionname"
            is TdApi.TextEntityTypeSpoiler -> "spoiler"
            is TdApi.TextEntityTypeBlockQuote -> "quote"
            is TdApi.TextEntityTypeExpandableBlockQuote -> "expandablequote"
            else -> return@mapNotNull null
        }
        if (entity.offset < 0 || entity.length <= 0 || entity.offset + entity.length > text.text.length) {
            return@mapNotNull null
        }
        MessageEntity(
            entity.offset,
            entity.length,
            name,
            when (type) {
                is TdApi.TextEntityTypeTextUrl -> type.url
                is TdApi.TextEntityTypeMentionName -> "tg://user?id=${type.userId}"
                else -> ""
            },
            if (type is TdApi.TextEntityTypePreCode) type.language else "",
        )
    }
}
