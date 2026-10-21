package ru.boltunya.ai

import kotlinx.serialization.json.Json
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import ru.boltunya.ai.research.ResearchContext
import ru.boltunya.ai.research.ResearchLinkPolicy
import ru.boltunya.domain.ChatMessage
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.CommunicationStyle
import ru.boltunya.memory.MemoryContext
import ru.boltunya.resources.ResourceCatalog
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

private const val MAX_MESSAGE_CHARACTERS = 1200
private const val MAX_CONTEXT_CHARACTERS = 12000
private const val MAX_PROMPT_CHARACTERS = 18000

private const val MAX_MEMORY_CONTEXT_CHARACTERS = 8000
private const val MEMORY_CONTEXT_OVERHEAD = 500
private const val MIN_RECENT_CONTEXT_CHARACTERS = 2400
private const val MAX_EXTERNAL_CONTEXT_CHARACTERS = 5500
private const val MIN_RESEARCH_CONTEXT_CHARACTERS = 400

private val resources = ResourceCatalog()

class PromptBuilder(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val personalStyle: PersonalStyleBuilder = PersonalStyleBuilder(),
    private val links: ResearchLinkPolicy = ResearchLinkPolicy(),
    private val authors: MessageContextFormatter = MessageContextFormatter(resources),
) {
    fun build(
        rule: ChatRule,
        history: List<ChatMessage>,
        globalPrompt: String = "",
        research: ResearchContext = ResearchContext(),
        styleHistory: List<ChatMessage> = history,
        memory: MemoryContext = MemoryContext(),
        images: Map<String, List<Media>> = emptyMap(),
    ): List<Message> {
        val style = CommunicationStyle.entries.firstOrNull { it.key == rule.style } ?: CommunicationStyle.FRIENDLY
        val tone = if (style == CommunicationStyle.CUSTOM && rule.resolvedStyleInstructions.isNotBlank()) {
            rule.resolvedStyleInstructions
        } else {
            resources.prompt(
                "ru/style/" + if (style == CommunicationStyle.CUSTOM) CommunicationStyle.FRIENDLY.key else style.key,
            )
        }
        val emoji = if (rule.emoji) {
            resources.prompt("ru/conversation/emoji-on")
        } else {
            resources.prompt("ru/conversation/emoji-off")
        }
        val now = ZonedDateTime.now(clock)
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm z"))
        val date = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val voice = if (rule.matchMyStyle) {
            personalStyle.build(styleHistory)
        } else {
            ""
        }
        val selectedStyle = resources.prompt(
            "ru/conversation/selected-style",
            mapOf("tone" to tone, "emoji" to emoji, "voice" to voice),
        )
        val baseSystem = resources.prompt(
            if (research.status == "none") "ru/conversation/system" else "ru/research/system",
            mapOf(
                "globalPrompt" to (globalPrompt),
                "instructions" to rule.instructions,
                "time" to time,
                "date" to date,
            ),
        ) + "\n" + resources.prompt("ru/conversation/authors")
        fun researchContent(data: String): String = resources.prompt(
            "ru/research/context",
            mapOf(
                "value0" to research.status,
                "value1" to data,
                "value2" to
                    resources.prompt(
                        if (links.visible(rule, history)) "ru/research/links-visible" else "ru/research/links-hidden",
                    ),
            ),
        )
        val externalOverhead = if (research.status ==
            "none"
        ) {
            resources.prompt("ru/research/no-data")
        } else {
            researchContent("")
        }
        val memoryBudget = minOf(
            MAX_MEMORY_CONTEXT_CHARACTERS,
            (
                MAX_PROMPT_CHARACTERS - baseSystem.length - selectedStyle.length - externalOverhead.length -
                    MIN_RECENT_CONTEXT_CHARACTERS -
                    MIN_RESEARCH_CONTEXT_CHARACTERS
                ).coerceAtLeast(0),
        )
        val longTerm = if (memory.isEmpty() || memoryBudget == 0) {
            ""
        } else {
            resources.prompt(
                "ru/memory/context",
                mapOf(
                    "memory" to
                        Json.encodeToString(memory.limited((memoryBudget - MEMORY_CONTEXT_OVERHEAD).coerceAtLeast(0))),
                ),
            )
        }
        val externalBudget =
            minOf(
                MAX_EXTERNAL_CONTEXT_CHARACTERS,
                (
                    MAX_PROMPT_CHARACTERS - baseSystem.length - selectedStyle.length - longTerm.length -
                        externalOverhead.length -
                        MIN_RECENT_CONTEXT_CHARACTERS
                    ).coerceAtLeast(0),
            )
        val external = if (research.status == "none") {
            externalOverhead
        } else {
            researchContent(Json.encodeToString(links.forPrompt(rule, history, research)).take(externalBudget))
        }
        val system = listOf(
            baseSystem,
            longTerm,
            external,
            selectedStyle,
        ).filter(String::isNotBlank).joinToString("\n\n")
        val candidates = history.asReversed().stream()
            .filter { !it.contextExcluded }
            .filter { it.text.isNotBlank() || images[it.id].orEmpty().isNotEmpty() }
            .map { message ->
                val text = if (!message.outgoing) {
                    authors.content(message, MAX_MESSAGE_CHARACTERS)
                } else {
                    message.text.take(MAX_MESSAGE_CHARACTERS)
                }
                if (message.outgoing) {
                    AssistantMessage.builder().content(text).build()
                } else {
                    UserMessage.builder().text(text).media(images[message.id].orEmpty()).build()
                }
            }.collect(Collectors.toList()).asReversed()

        var characters = 0
        val contextBudget = minOf(MAX_CONTEXT_CHARACTERS, MAX_PROMPT_CHARACTERS - system.length)
        val selected = candidates.asReversed().stream().takeWhile {
            characters += it.toString().length
            characters <= contextBudget
        }.collect(Collectors.toList()).asReversed()

        val context = MessageWindowChatMemory.builder().maxMessages(rule.contextMessages + 1).build()
        val conversationId = history.lastOrNull()?.chatId?.ifBlank { "preview" } ?: "preview"
        context.add(conversationId, listOf(SystemMessage(system)) + selected)
        return context.get(conversationId)
    }
}
