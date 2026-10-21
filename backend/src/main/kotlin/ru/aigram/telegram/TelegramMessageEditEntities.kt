package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.MessageEditEntity
import ru.aigram.domain.chat.MessageEditEntityKind
import ru.aigram.exception.impl.InvalidMessageEditException
import java.util.Locale
import java.util.stream.Collectors
import java.util.stream.IntStream

@Component
class TelegramMessageEditEntities {
    fun describe(original: TdApi.FormattedText): List<MessageEditEntity> =
        IntStream.range(0, original.entities.orEmpty().size).mapToObj { index ->
            val entity = original.entities[index]
            MessageEditEntity(
                entity.offset,
                entity.length,
                MessageEditEntityKind.EXISTING,
                index,
                hint(entity.type),
                (entity.type as? TdApi.TextEntityTypeTextUrl)?.url.orEmpty(),
                (entity.type as? TdApi.TextEntityTypePreCode)?.language.orEmpty(),
            )
        }.toList()

    fun format(text: String, entities: List<MessageEditEntity>, original: TdApi.FormattedText): TdApi.FormattedText {
        if (invalidUnicode(text)) {
            throw InvalidMessageEditException()
        }
        val result = entities.stream().map { entity ->
            if (entity.offset < 0 || entity.length <= 0 || entity.offset > text.length ||
                entity.length > text.length - entity.offset ||
                !boundary(text, entity.offset) || !boundary(text, entity.offset + entity.length)
            ) {
                throw InvalidMessageEditException()
            }
            val type = if (entity.kind == MessageEditEntityKind.EXISTING) {
                val existing = original.entities.orEmpty().getOrNull(existingIndex(entity))?.type
                if (existing == null) {
                    throw InvalidMessageEditException()
                }
                existing
            } else {
                if (entity.sourceIndex != null) {
                    throw InvalidMessageEditException()
                }
                create(entity)
            }
            TdApi.TextEntity(entity.offset, entity.length, type)
        }.toList()
        entities.stream().filter { it.kind == MessageEditEntityKind.EXISTING }
            .collect(Collectors.groupingBy(::existingIndex)).values.stream().forEach { fragments ->
                val sorted = fragments.stream().sorted(compareBy { it.offset }).toList()
                if (IntStream.range(1, sorted.size).anyMatch {
                        sorted[it].offset < sorted[it - 1].offset + sorted[it - 1].length
                    }
                ) {
                    throw InvalidMessageEditException()
                }
            }
        return TdApi.FormattedText(text, result.toTypedArray())
    }

    fun invalidUnicode(text: String): Boolean = IntStream.range(0, text.length).anyMatch { index ->
        val current = text[index]
        Character.isHighSurrogate(current) &&
            (index + 1 >= text.length || !Character.isLowSurrogate(text[index + 1])) ||
            Character.isLowSurrogate(current) && (index == 0 || !Character.isHighSurrogate(text[index - 1]))
    }

    private fun boundary(text: String, index: Int): Boolean = index == 0 || index == text.length ||
        !Character.isHighSurrogate(text[index - 1]) || !Character.isLowSurrogate(text[index])

    private fun existingIndex(entity: MessageEditEntity): Int {
        val index = entity.sourceIndex
        if (index == null || index < 0) {
            throw InvalidMessageEditException()
        }
        return index
    }

    private fun create(entity: MessageEditEntity): TdApi.TextEntityType = when (entity.kind) {
        MessageEditEntityKind.BOLD -> TdApi.TextEntityTypeBold()
        MessageEditEntityKind.ITALIC -> TdApi.TextEntityTypeItalic()
        MessageEditEntityKind.UNDERLINE -> TdApi.TextEntityTypeUnderline()
        MessageEditEntityKind.STRIKE -> TdApi.TextEntityTypeStrikethrough()
        MessageEditEntityKind.CODE -> TdApi.TextEntityTypeCode()
        MessageEditEntityKind.PRE -> TdApi.TextEntityTypePre()
        MessageEditEntityKind.PRECODE -> TdApi.TextEntityTypePreCode(entity.language)
        MessageEditEntityKind.TEXTURL -> {
            if (entity.url.isBlank()) {
                throw InvalidMessageEditException()
            }
            TdApi.TextEntityTypeTextUrl(entity.url)
        }
        MessageEditEntityKind.SPOILER -> TdApi.TextEntityTypeSpoiler()
        MessageEditEntityKind.QUOTE -> TdApi.TextEntityTypeBlockQuote()
        MessageEditEntityKind.EXPANDABLEQUOTE -> TdApi.TextEntityTypeExpandableBlockQuote()
        MessageEditEntityKind.EXISTING -> throw InvalidMessageEditException()
    }

    private fun hint(type: TdApi.TextEntityType): String = when (type) {
        is TdApi.TextEntityTypeBold -> "bold"
        is TdApi.TextEntityTypeItalic -> "italic"
        is TdApi.TextEntityTypeUnderline -> "underline"
        is TdApi.TextEntityTypeStrikethrough -> "strike"
        is TdApi.TextEntityTypeCode -> "code"
        is TdApi.TextEntityTypePre -> "pre"
        is TdApi.TextEntityTypePreCode -> "precode"
        is TdApi.TextEntityTypeTextUrl -> "texturl"
        is TdApi.TextEntityTypeUrl -> "url"
        is TdApi.TextEntityTypeEmailAddress -> "email"
        is TdApi.TextEntityTypePhoneNumber -> "phone"
        is TdApi.TextEntityTypeMention -> "mention"
        is TdApi.TextEntityTypeMentionName -> "mentionname"
        is TdApi.TextEntityTypeCustomEmoji -> "customemoji"
        is TdApi.TextEntityTypeSpoiler -> "spoiler"
        is TdApi.TextEntityTypeBlockQuote -> "quote"
        is TdApi.TextEntityTypeExpandableBlockQuote -> "expandablequote"
        else -> type.javaClass.simpleName.removePrefix("TextEntityType").lowercase(Locale.ROOT)
    }
}
