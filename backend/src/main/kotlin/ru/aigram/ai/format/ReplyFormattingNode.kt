package ru.aigram.ai.format

import org.commonmark.node.CustomNode
import ru.aigram.domain.chat.OutgoingEntityType

internal class ReplyFormattingNode(val type: OutgoingEntityType) : CustomNode()
