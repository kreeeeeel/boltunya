package ru.aigram.ai.format

import org.commonmark.node.Nodes
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun
import ru.aigram.domain.chat.OutgoingEntityType
import java.util.stream.StreamSupport

private const val DELIMITER_LENGTH = 2

internal class ReplyDelimiterProcessor(private val delimiter: Char, private val type: OutgoingEntityType) :
    DelimiterProcessor {
    override fun getOpeningCharacter(): Char = delimiter
    override fun getClosingCharacter(): Char = delimiter
    override fun getMinLength(): Int = DELIMITER_LENGTH

    override fun process(openingRun: DelimiterRun, closingRun: DelimiterRun): Int {
        if (openingRun.length() != DELIMITER_LENGTH || closingRun.length() != DELIMITER_LENGTH) {
            return 0
        }
        val node = ReplyFormattingNode(type)
        StreamSupport.stream(Nodes.between(openingRun.opener, closingRun.closer).spliterator(), false)
            .toList().stream().forEach(node::appendChild)
        openingRun.opener.insertAfter(node)
        return DELIMITER_LENGTH
    }
}
