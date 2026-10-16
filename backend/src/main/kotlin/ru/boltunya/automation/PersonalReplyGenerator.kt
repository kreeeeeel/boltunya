package ru.boltunya.automation

import jakarta.inject.Inject
import ru.boltunya.ai.ConversationService
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.http.InputValidation
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
    private val validation: InputValidation,
) {
    fun reply(rule: ChatRule, history: List<ChatMessage>): ReplyResult {
        val resolved = settings.resolve(rule)
        validation.model(resolved.model, forAutomation = true)
        val context = history.map { it.copy(automated = it.automated || automated.contains(it.chatId, it.id)) }
        return conversation.reply(
            resolved,
            context,
            settings.get().globalPrompt,
            if (rule.matchMyStyle) {
                writingStyle.update(
                    context.last().chatId,
                    context.filter { !it.automated },
                )
            } else {
                emptyList()
            },
            memory.context(rule, context),
        )
    }
}
