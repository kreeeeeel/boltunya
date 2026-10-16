package ru.boltunya.automation

import jakarta.inject.Inject
import ru.boltunya.ai.ConversationService
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.memory.MemoryCoordinator
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.storage.WritingStyleStore

class PersonalReplyGenerator @Inject constructor(
    private val settings: SettingsStore,
    private val conversation: ConversationService,
    private val automated: AutomatedReplyStore,
    private val writingStyle: WritingStyleStore,
    private val memory: MemoryCoordinator,
) {
    fun reply(rule: ChatRule, history: List<ChatMessage>): ReplyResult = conversation.reply(
        settings.resolve(rule),
        history,
        settings.get().globalPrompt,
        if (rule.matchMyStyle) {
            writingStyle.update(
                history.last().chatId,
                history.filter {
                    !automated.contains(it.chatId, it.id)
                },
            )
        } else {
            emptyList()
        },
        memory.context(rule, history),
    )
}
