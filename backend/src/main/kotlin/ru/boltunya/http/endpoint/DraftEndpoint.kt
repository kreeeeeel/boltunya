package ru.boltunya.http.endpoint

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import ru.boltunya.ai.profile.ModelTargetResolver
import ru.boltunya.ai.profile.ModelTask
import ru.boltunya.automation.PersonalReplyGenerator
import ru.boltunya.automation.ReplyCoordinator
import ru.boltunya.automation.activity.ReplyTraceScope
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.PreviewJob
import ru.boltunya.exception.impl.ChatUnavailableException
import ru.boltunya.exception.impl.InvalidHistoryIdentifierException
import ru.boltunya.http.InputValidation
import ru.boltunya.storage.ContextExclusionStore
import ru.boltunya.storage.SettingsStore
import ru.boltunya.telegram.TelegramGateway

private const val STYLE_HISTORY_SIZE = 100

class DraftEndpoint @Inject constructor(
    private val json: Json,
    private val settings: SettingsStore,
    private val telegram: TelegramGateway,
    private val validation: InputValidation,
    private val replies: ReplyCoordinator,
    private val generator: PersonalReplyGenerator,
    private val targets: ModelTargetResolver,
    private val exclusions: ContextExclusionStore,
    private val tracing: ReplyTraceScope,
) {
    fun enqueue(body: JsonObject): PreviewJob {
        val id = body["chatId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val submitted = body["rule"]?.let { json.decodeFromJsonElement<ChatRule>(it) } ?: settings.rule(id)
        val rule = settings.resolve(submitted)
        validation.validate(rule)
        validation.target(rule)
        val messageId = body["messageId"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val selected = messageId.toLongOrNull()
        if (messageId.isNotEmpty() &&
            (selected == null || selected <= 0 || selected == Long.MAX_VALUE || selected.toString() != messageId)
        ) {
            throw InvalidHistoryIdentifierException()
        }
        return replies.enqueueDraft(id, targets.resolve(ModelTask.REPLY, rule).model) {
            val history = telegram.history(
                id,
                if (rule.matchMyStyle) STYLE_HISTORY_SIZE else rule.contextMessages,
                if (selected ==
                    null
                ) {
                    0
                } else {
                    selected + 1
                },
            )
            if (history.isEmpty()) throw ChatUnavailableException()
            val source = if (messageId.isEmpty()) {
                history.lastOrNull {
                    !it.outgoing && !it.deleted
                }
            } else {
                history.lastOrNull()
            }
            if (source == null || source.outgoing || source.deleted ||
                messageId.isNotEmpty() && source.id != messageId
            ) {
                throw ChatUnavailableException()
            }
            if (exclusions.forInference(
                    telegram.status().userId,
                    listOf(source),
                ).isEmpty()
            ) {
                throw ChatUnavailableException()
            }
            tracing.current()?.updateTrigger(source.text)
            generator.draft(rule, history)
        }
    }

    fun job(id: String): PreviewJob = replies.draft(id)
}
