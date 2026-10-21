package ru.boltunya.automation

import jakarta.inject.Inject
import ru.boltunya.ai.ConversationService
import ru.boltunya.ai.ReplyImageContext
import ru.boltunya.ai.profile.ModelTargetResolver
import ru.boltunya.ai.profile.ModelTask
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.ReplyResult
import ru.boltunya.exception.impl.ChatUnavailableException
import ru.boltunya.http.InputValidation
import ru.boltunya.memory.MemoryCoordinator
import ru.boltunya.storage.AutomatedReplyStore
import ru.boltunya.storage.ContextExclusionStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.storage.WritingStyleStore
import ru.boltunya.telegram.TelegramGateway
import java.util.stream.Stream

class PersonalReplyGenerator @Inject constructor(
    private val settings: SettingsStore,
    private val conversation: ConversationService,
    private val automated: AutomatedReplyStore,
    private val writingStyle: WritingStyleStore,
    private val memory: MemoryCoordinator,
    private val validation: InputValidation,
    private val images: ReplyImageContext,
    private val targets: ModelTargetResolver,
    private val telegram: TelegramGateway,
    private val exclusions: ContextExclusionStore,
) {
    fun reply(rule: ChatRule, history: List<ChatMessage>): ReplyResult = generate(rule, history, true)

    fun draft(rule: ChatRule, history: List<ChatMessage>): ReplyResult =
        generate(rule.copy(aiStickers = false), history, false)

    private fun generate(rule: ChatRule, history: List<ChatMessage>, forAutomation: Boolean): ReplyResult {
        val resolved = settings.resolve(rule)
        val owner = telegram.status().userId
        val context = exclusions.forInference(
            owner,
            history.map {
                it.copy(
                    automated =
                    it.automated || automated.contains(it.chatId, it.id),
                )
            },
        )
        val attachments = images.build(resolved, context)
        if (attachments.isEmpty()) validation.target(resolved, forAutomation)
        if (context.isEmpty()) throw ChatUnavailableException()
        val task = if (attachments.isEmpty()) ModelTask.REPLY else ModelTask.VISION
        val stickers = if (rule.aiStickers && targets.resolve(task, resolved).capabilities.structuredOutput) {
            val catalog = telegram.stickers()
            Stream.concat(catalog.favorites.stream(), catalog.recent.stream()).distinct().limit(24).toList()
        } else {
            emptyList()
        }
        return conversation.reply(
            resolved,
            context,
            settings.get().globalPrompt,
            if (rule.matchMyStyle) {
                exclusions.forInference(
                    owner,
                    writingStyle.update(
                        context.last().chatId,
                        context.filter { !it.automated },
                    ),
                )
            } else {
                emptyList()
            },
            memory.context(rule, context),
            attachments,
            task,
            stickers,
        )
    }
}
