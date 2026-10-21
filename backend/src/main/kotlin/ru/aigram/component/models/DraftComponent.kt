package ru.aigram.component.models

import org.springframework.stereotype.Component
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.automation.PersonalReplyGenerator
import ru.aigram.automation.ReplyCoordinator
import ru.aigram.automation.activity.ReplyTraceScope
import ru.aigram.domain.ai.PreviewJob
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.speech.DraftOutput
import ru.aigram.exception.impl.ChatUnavailableException
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import ru.aigram.telegram.TelegramGateway
import ru.aigram.validation.InputValidation

private const val STYLE_HISTORY_SIZE = 100

@Component
class DraftComponent(
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val validation: InputValidation,
    private val replies: ReplyCoordinator,
    private val generator: PersonalReplyGenerator,
    private val targets: ModelTargetResolver,
    private val exclusions: ContextExclusionStore,
    private val tracing: ReplyTraceScope,
) {
    fun enqueue(
        chatId: String,
        submitted: ChatRule?,
        messageId: String,
        output: DraftOutput = DraftOutput.TEXT,
    ): PreviewJob {
        val rule = settings.resolve(submitted ?: settings.rule(chatId))
        validation.validate(rule)
        validation.target(rule)
        val selected = messageId.toLongOrNull()
        if (messageId.isNotEmpty() &&
            (selected == null || selected <= 0 || selected == Long.MAX_VALUE || selected.toString() != messageId)
        ) {
            throw InvalidHistoryIdentifierException()
        }
        return replies.enqueueDraft(chatId, targets.resolve(ModelTask.REPLY, rule).model) {
            val limit = if (rule.matchMyStyle) {
                STYLE_HISTORY_SIZE
            } else {
                rule.contextMessages
            }
            val before = if (selected == null) {
                0
            } else {
                selected + 1
            }
            val history = telegram.history(chatId, limit, before)
            val source = if (messageId.isEmpty()) {
                history.lastOrNull { !it.outgoing && !it.deleted }
            } else {
                history.lastOrNull()
            }
            if (source == null || source.outgoing || source.deleted ||
                messageId.isNotEmpty() && source.id != messageId
            ) {
                throw ChatUnavailableException()
            }
            if (exclusions.forInference(telegram.status().userId, listOf(source)).isEmpty()) {
                throw ChatUnavailableException()
            }
            tracing.current()?.updateTrigger(source.text)
            generator.draft(rule, history, output)
        }
    }

    fun job(id: String): PreviewJob = replies.draft(id)
}
