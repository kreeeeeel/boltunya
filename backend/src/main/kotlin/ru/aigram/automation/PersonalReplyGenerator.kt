package ru.aigram.automation

import org.springframework.stereotype.Component
import ru.aigram.ai.ConversationService
import ru.aigram.ai.ReplyImageContext
import ru.aigram.ai.profile.ModelTargetResolver
import ru.aigram.domain.ai.ReplyResult
import ru.aigram.domain.ai.profile.ModelTask
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.speech.DraftOutput
import ru.aigram.exception.impl.ChatUnavailableException
import ru.aigram.memory.MemoryCoordinator
import ru.aigram.resources.ResourceCatalog
import ru.aigram.speech.reply.SpeechInputContext
import ru.aigram.speech.reply.VoiceReplyPolicy
import ru.aigram.speech.reply.VoiceReplyService
import ru.aigram.storage.AutomatedReplyStore
import ru.aigram.storage.ContextExclusionStore
import ru.aigram.storage.SettingsStore
import ru.aigram.storage.WritingStyleStore
import ru.aigram.telegram.TelegramGateway
import ru.aigram.validation.InputValidation
import java.util.stream.Stream

@Component
class PersonalReplyGenerator(
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
    private val resources: ResourceCatalog,
    private val speechInput: SpeechInputContext,
    private val voicePolicy: VoiceReplyPolicy,
    private val voices: VoiceReplyService,
) {
    fun reply(rule: ChatRule, history: List<ChatMessage>): ReplyResult =
        render(rule, history, true, voicePolicy.wantsVoice(settings.get(), history.lastOrNull()?.kind.orEmpty()))

    fun draft(rule: ChatRule, history: List<ChatMessage>, output: DraftOutput = DraftOutput.TEXT): ReplyResult =
        render(rule.copy(aiStickers = false), history, false, output == DraftOutput.VOICE)

    fun initiate(rule: ChatRule, history: List<ChatMessage>): ReplyResult = render(
        rule.copy(aiStickers = false, internetAccess = false),
        history,
        true,
        voicePolicy.wantsVoice(settings.get(), history.lastOrNull()?.kind.orEmpty()),
        initiative = true,
    )

    private fun render(
        rule: ChatRule,
        history: List<ChatMessage>,
        forAutomation: Boolean,
        voiceOutput: Boolean,
        initiative: Boolean = false,
    ): ReplyResult {
        val preferences = settings.get()
        val owner = telegram.status().userId
        if (voiceOutput) {
            voices.ensureReady(preferences)
        }
        val result =
            generate(
                rule.copy(aiStickers = rule.aiStickers && !voiceOutput),
                history,
                forAutomation,
                initiative,
                voiceOutput,
            )
        return if (voiceOutput) {
            voices.compose(result, history.last().chatId, owner, preferences)
        } else {
            result
        }
    }

    private fun generate(
        rule: ChatRule,
        history: List<ChatMessage>,
        forAutomation: Boolean,
        initiative: Boolean = false,
        voiceOutput: Boolean = false,
    ): ReplyResult {
        val resolved = settings.resolve(rule)
        val preferences = settings.get()
        val owner = telegram.status().userId
        val context = speechInput.prepare(
            exclusions.forInference(
                owner,
                history.map {
                    it.copy(
                        automated =
                        it.automated || automated.contains(it.chatId, it.id),
                    )
                },
            ),
            ownerId = owner,
        )
        val attachments = images.build(resolved, context)
        if (attachments.isEmpty()) {
            validation.target(resolved, forAutomation)
        }
        if (context.isEmpty()) {
            throw ChatUnavailableException()
        }
        val task = if (attachments.isEmpty()) {
            ModelTask.REPLY
        } else {
            ModelTask.VISION
        }
        val stickers = if (rule.aiStickers && targets.resolve(task, resolved).capabilities.structuredOutput) {
            val catalog = telegram.stickers()
            Stream.concat(catalog.favorites.stream(), catalog.recent.stream()).distinct().limit(24).toList()
        } else {
            emptyList()
        }
        return conversation.reply(
            resolved,
            context,
            preferences.globalPrompt + (
                if (voiceOutput) {
                    "\n\n" + resources.prompt("ru/conversation/voice-output")
                } else {
                    ""
                }
                ) + if (initiative) {
                "\n\n" + resources.prompt("ru/conversation/initiative")
            } else {
                ""
            },
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
            formatReplies = preferences.formatReplies && !voiceOutput,
        )
    }
}
