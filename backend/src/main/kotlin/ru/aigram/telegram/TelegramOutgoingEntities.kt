package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.springframework.stereotype.Component
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.domain.chat.OutgoingEntityType
import ru.aigram.exception.impl.InvalidMessageEntitiesException
import ru.aigram.validation.messageEntityBoundary
import ru.aigram.validation.validMessageLink
import ru.aigram.validation.validMessageUnicode
import java.util.stream.IntStream

private const val MAX_OUTGOING_ENTITIES = 100
private const val MAX_LANGUAGE_LENGTH = 64

@Component
class TelegramOutgoingEntities {
    fun format(text: String, entities: List<MessageEntity>): TdApi.FormattedText {
        if (entities.size > MAX_OUTGOING_ENTITIES || !validMessageUnicode(text)) {
            throw InvalidMessageEntitiesException()
        }
        val types = entities.stream().map { entity ->
            if (entity.offset < 0 || entity.length <= 0 || entity.offset > text.length ||
                entity.length > text.length - entity.offset || !messageEntityBoundary(text, entity.offset) ||
                !messageEntityBoundary(text, entity.offset + entity.length)
            ) {
                throw InvalidMessageEntitiesException()
            }
            val type = OutgoingEntityType.entries.stream().filter { it.value == entity.type }.findFirst().orElse(null)
            if (type == null || type != OutgoingEntityType.TEXTURL && entity.url.isNotEmpty() ||
                type != OutgoingEntityType.PRECODE && entity.language.isNotEmpty() ||
                entity.language.length > MAX_LANGUAGE_LENGTH ||
                entity.language.codePoints().anyMatch { Character.isISOControl(it) || Character.isWhitespace(it) } ||
                type == OutgoingEntityType.TEXTURL && !validMessageLink(entity.url)
            ) {
                throw InvalidMessageEntitiesException()
            }
            type
        }.toList()
        IntStream.range(0, entities.size).forEach { first ->
            IntStream.range(first + 1, entities.size).forEach { second ->
                validateOverlap(entities[first], types[first], entities[second], types[second])
            }
        }
        val native = IntStream.range(0, entities.size).mapToObj { index ->
            val entity = entities[index]
            TdApi.TextEntity(entity.offset, entity.length, nativeType(types[index], entity))
        }.sorted(compareBy<TdApi.TextEntity> { it.offset }.thenByDescending { it.length }.thenBy { priority(it.type) })
            .toList().toTypedArray()
        return TdApi.FormattedText(text, native)
    }

    private fun validateOverlap(
        first: MessageEntity,
        firstType: OutgoingEntityType,
        second: MessageEntity,
        secondType: OutgoingEntityType,
    ) {
        val firstEnd = first.offset + first.length
        val secondEnd = second.offset + second.length
        if (first.offset >= secondEnd || second.offset >= firstEnd) {
            return
        }
        val firstContains = first.offset <= second.offset && firstEnd >= secondEnd
        val secondContains = second.offset <= first.offset && secondEnd >= firstEnd
        if (!firstContains && !secondContains || firstType == secondType ||
            firstType.quotation() && secondType.quotation() ||
            firstType == OutgoingEntityType.TEXTURL && secondType == OutgoingEntityType.TEXTURL ||
            firstType == OutgoingEntityType.TEXTURL && secondType.quotation() && firstContains && !secondContains ||
            secondType == OutgoingEntityType.TEXTURL && firstType.quotation() && secondContains && !firstContains ||
            firstType.monospaced() && !(secondType.quotation() && secondContains) ||
            secondType.monospaced() && !(firstType.quotation() && firstContains)
        ) {
            throw InvalidMessageEntitiesException()
        }
    }

    private fun priority(type: TdApi.TextEntityType): Int = when (type) {
        is TdApi.TextEntityTypeBlockQuote, is TdApi.TextEntityTypeExpandableBlockQuote -> 0
        is TdApi.TextEntityTypeCode, is TdApi.TextEntityTypePre, is TdApi.TextEntityTypePreCode -> 1
        is TdApi.TextEntityTypeTextUrl -> 2
        else -> 3
    }

    private fun nativeType(type: OutgoingEntityType, entity: MessageEntity): TdApi.TextEntityType = when (type) {
        OutgoingEntityType.BOLD -> TdApi.TextEntityTypeBold()
        OutgoingEntityType.ITALIC -> TdApi.TextEntityTypeItalic()
        OutgoingEntityType.UNDERLINE -> TdApi.TextEntityTypeUnderline()
        OutgoingEntityType.STRIKE -> TdApi.TextEntityTypeStrikethrough()
        OutgoingEntityType.SPOILER -> TdApi.TextEntityTypeSpoiler()
        OutgoingEntityType.CODE -> TdApi.TextEntityTypeCode()
        OutgoingEntityType.PRE -> TdApi.TextEntityTypePre()
        OutgoingEntityType.PRECODE -> TdApi.TextEntityTypePreCode(entity.language)
        OutgoingEntityType.TEXTURL -> TdApi.TextEntityTypeTextUrl(entity.url)
        OutgoingEntityType.QUOTE -> TdApi.TextEntityTypeBlockQuote()
        OutgoingEntityType.EXPANDABLEQUOTE -> TdApi.TextEntityTypeExpandableBlockQuote()
    }
}
