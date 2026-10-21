package ru.aigram.ai

import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import org.springframework.stereotype.Component
import ru.aigram.ai.format.ReplyDelimiterProcessor
import ru.aigram.ai.format.ReplyTextVisitor
import ru.aigram.domain.ai.FormattedReply
import ru.aigram.domain.chat.OutgoingEntityType

private const val MAX_MARKDOWN_NESTING = 64

@Component
class ReplyTextFormatter {
    private val parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        .maxInlineNesting(MAX_MARKDOWN_NESTING)
        .maxOpenBlockParsers(MAX_MARKDOWN_NESTING)
        .customDelimiterProcessor(ReplyDelimiterProcessor('~', OutgoingEntityType.STRIKE))
        .customDelimiterProcessor(ReplyDelimiterProcessor('|', OutgoingEntityType.SPOILER))
        .build()

    fun format(text: String, enabled: Boolean): FormattedReply {
        if (!enabled || text.isEmpty()) {
            return FormattedReply(text)
        }
        val visitor = ReplyTextVisitor(text)
        parser.parse(text).accept(visitor)
        return visitor.result()
    }
}
