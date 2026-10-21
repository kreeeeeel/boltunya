package ru.aigram.ai.format

import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import ru.aigram.domain.ai.FormattedReply
import ru.aigram.domain.chat.MessageEntity
import ru.aigram.domain.chat.OutgoingEntityType
import ru.aigram.validation.validMessageLink
import java.util.stream.IntStream
import java.util.stream.Stream

private const val MAX_CODE_LANGUAGE_LENGTH = 64
private const val MAX_FORMATTED_ENTITIES = 100

internal class ReplyTextVisitor(private val source: String) : AbstractVisitor() {
    private val text = StringBuilder()
    private val entities = mutableListOf<MessageEntity>()
    private val active = mutableSetOf<OutgoingEntityType>()
    private var literalFallback = false

    fun result(): FormattedReply {
        if (literalFallback || entities.isEmpty()) {
            return FormattedReply(source)
        }
        val monospaced = entities.stream().filter {
            it.type == OutgoingEntityType.CODE.value || it.type == OutgoingEntityType.PRE.value ||
                it.type == OutgoingEntityType.PRECODE.value
        }.toList()
        val compatible = entities.stream().flatMap { entity ->
            if (monospaced.contains(entity) || entity.type == OutgoingEntityType.QUOTE.value) {
                Stream.of(entity)
            } else {
                var ranges = listOf(entity)
                monospaced.stream().forEach { code ->
                    ranges = ranges.stream().flatMap { subtract(it, code).stream() }.toList()
                }
                ranges.stream()
            }
        }.sorted(compareBy<MessageEntity> { it.offset }.thenByDescending { it.length }).toList()
        if (compatible.size > MAX_FORMATTED_ENTITIES) {
            return FormattedReply(source)
        }
        val prefix = source.takeWhile { it == '\n' }
        val rendered = prefix + text
        val trailing = source.length - source.indexOfLast { it != '\n' } - 1
        val existing = rendered.length - rendered.indexOfLast { it != '\n' } - 1
        return FormattedReply(
            rendered + "\n".repeat((trailing - existing).coerceAtLeast(0)),
            compatible.stream().map { it.copy(offset = it.offset + prefix.length) }.toList(),
        )
    }

    private fun subtract(entity: MessageEntity, code: MessageEntity): List<MessageEntity> {
        val end = entity.offset + entity.length
        val codeEnd = code.offset + code.length
        if (code.offset >= end || codeEnd <= entity.offset) {
            return listOf(entity)
        }
        return Stream.of(
            entity.copy(length = (code.offset - entity.offset).coerceAtLeast(0)),
            entity.copy(
                offset = maxOf(entity.offset, codeEnd),
                length = (end - maxOf(entity.offset, codeEnd)).coerceAtLeast(0),
            ),
        ).filter { it.length > 0 }.toList()
    }

    override fun visit(node: Text) {
        text.append(node.literal)
    }

    override fun visit(node: SoftLineBreak) {
        text.append('\n')
    }

    override fun visit(node: HardLineBreak) {
        text.append('\n')
    }

    override fun visit(node: Emphasis) = span(node, OutgoingEntityType.ITALIC)

    override fun visit(node: StrongEmphasis) = span(node, OutgoingEntityType.BOLD)

    override fun visit(node: BlockQuote) {
        span(node, OutgoingEntityType.QUOTE)
        separate(node)
    }

    override fun visit(node: Code) {
        literal(node.literal, OutgoingEntityType.CODE)
    }

    override fun visit(node: FencedCodeBlock) {
        val language = node.info.orEmpty().trim().takeWhile { !it.isWhitespace() }
        if (language.length > MAX_CODE_LANGUAGE_LENGTH || language.codePoints().anyMatch(Character::isISOControl)) {
            literal(node.literal, OutgoingEntityType.PRE)
            separate(node)
            return
        }
        val type = if (language.isEmpty()) {
            OutgoingEntityType.PRE
        } else {
            OutgoingEntityType.PRECODE
        }
        literal(node.literal, type, language)
        separate(node)
    }

    override fun visit(node: IndentedCodeBlock) {
        literal(node.literal, OutgoingEntityType.PRE)
        separate(node)
    }

    override fun visit(node: HtmlInline) {
        text.append(node.literal)
    }

    override fun visit(node: HtmlBlock) {
        text.append(node.literal)
        separate(node)
    }

    override fun visit(node: Link) {
        if (validMessageLink(node.destination)) {
            span(node, OutgoingEntityType.TEXTURL, node.destination)
        } else {
            literalFallback = true
            text.append(raw(node))
        }
    }

    override fun visit(node: Image) {
        literalFallback = true
        text.append(raw(node))
    }

    override fun visit(node: LinkReferenceDefinition) = Unit

    override fun visit(node: Paragraph) {
        visitChildren(node)
        separate(node)
    }

    override fun visit(node: Heading) {
        span(node, OutgoingEntityType.BOLD)
        separate(node)
    }

    override fun visit(node: ListItem) {
        val list = node.parent
        val number = if (list is OrderedList) {
            (list.markerStartNumber ?: 1) + Stream.iterate(node.previous, { it != null }, { it.previous }).count()
        } else {
            null
        }
        val prefix = if (number == null) {
            "• "
        } else {
            "$number. "
        }
        text.append(prefix)
        visitChildren(node)
        separate(node)
    }

    override fun visit(node: BulletList) {
        visitChildren(node)
        separate(node)
    }

    override fun visit(node: OrderedList) {
        visitChildren(node)
        separate(node)
    }

    override fun visit(node: ThematicBreak) {
        text.append(raw(node))
        separate(node)
    }

    override fun visit(node: CustomNode) {
        if (node is ReplyFormattingNode) {
            span(node, node.type)
        } else {
            literalFallback = true
            text.append(raw(node))
        }
    }

    override fun visit(node: CustomBlock) {
        literalFallback = true
        text.append(raw(node))
    }

    private fun literal(value: String, type: OutgoingEntityType, language: String = "") {
        val offset = text.length
        text.append(value)
        if (value.isNotEmpty()) {
            entities.add(MessageEntity(offset, value.length, type.value, language = language))
        }
    }

    private fun span(node: Node, type: OutgoingEntityType, url: String = "") {
        if (!active.add(type)) {
            visitChildren(node)
            return
        }
        val offset = text.length
        visitChildren(node)
        active.remove(type)
        if (text.length > offset) {
            entities.add(MessageEntity(offset, text.length - offset, type.value, url))
        }
    }

    private fun separate(node: Node) {
        if (node.next != null) {
            val separator = if (node.parent is ListItem || node is ListItem) {
                "\n"
            } else {
                "\n\n"
            }
            IntStream.range(0, separator.length).forEach {
                if (!text.endsWith(separator)) {
                    text.append('\n')
                }
            }
        }
    }

    private fun raw(node: Node): String = node.sourceSpans.stream().map {
        source.substring(it.inputIndex, it.inputIndex + it.length)
    }.toList().joinToString("\n")
}
