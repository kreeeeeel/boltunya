package ru.aigram.ai

import kotlinx.serialization.json.Json
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.content.Media
import ru.aigram.ai.profile.ProfileContextBudget
import ru.aigram.ai.prompt.PromptTokenEstimator
import ru.aigram.ai.research.ResearchLinkPolicy
import ru.aigram.domain.ai.profile.ResolvedModelTarget
import ru.aigram.domain.ai.research.ResearchContext
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.CommunicationStyle
import ru.aigram.domain.memory.MemoryContext
import ru.aigram.resources.ResourceCatalog
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import java.util.stream.IntStream

private const val MAX_MESSAGE_CHARACTERS = 1200
private const val MAX_CONTEXT_CHARACTERS = 12000
private const val MAX_PROMPT_CHARACTERS = 18000

private const val MAX_MEMORY_CONTEXT_CHARACTERS = 8000
private const val MEMORY_CONTEXT_OVERHEAD = 500
private const val MIN_RECENT_CONTEXT_CHARACTERS = 2400
private const val MAX_EXTERNAL_CONTEXT_CHARACTERS = 5500
private const val MIN_RESEARCH_CONTEXT_CHARACTERS = 400
private const val MEMORY_BUDGET_SEARCH_STEPS = 16
private const val RECENT_HISTORY_RESERVE_TOKENS = 256

private val resources = ResourceCatalog()

class PromptBuilder(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val personalStyle: PersonalStyleBuilder = PersonalStyleBuilder(),
    private val links: ResearchLinkPolicy = ResearchLinkPolicy(),
    private val authors: MessageContextFormatter = MessageContextFormatter(),
    private val tokens: PromptTokenEstimator = PromptTokenEstimator(),
    private val budget: ProfileContextBudget = ProfileContextBudget(),
) {
    fun build(
        rule: ChatRule,
        history: List<ChatMessage>,
        globalPrompt: String = "",
        research: ResearchContext = ResearchContext(),
        styleHistory: List<ChatMessage> = history,
        memory: MemoryContext = MemoryContext(),
        images: Map<String, List<Media>> = emptyMap(),
        inputTarget: ResolvedModelTarget? = null,
        reservedOutputTokens: Int = rule.maxTokens,
        formatReplies: Boolean = false,
    ): List<Message> {
        val style = CommunicationStyle.entries.firstOrNull { it.key == rule.style } ?: CommunicationStyle.FRIENDLY
        val tone = if (style == CommunicationStyle.CUSTOM && rule.resolvedStyleInstructions.isNotBlank()) {
            rule.resolvedStyleInstructions
        } else {
            resources.prompt(
                "ru/style/" + if (style == CommunicationStyle.CUSTOM) {
                    CommunicationStyle.FRIENDLY.key
                } else {
                    style.key
                },
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
            if (research.status == "none") {
                "ru/conversation/system"
            } else {
                "ru/research/system"
            },
            mapOf(
                "globalPrompt" to (globalPrompt),
                "instructions" to rule.instructions,
                "time" to time,
                "date" to date,
            ),
        ) + "\n" + resources.prompt("ru/conversation/authors") + "\n" +
            resources.prompt("ru/conversation/mentions") + "\n" +
            resources.prompt("ru/conversation/output") + "\n" + resources.prompt(
                if (formatReplies) {
                    "ru/conversation/formatting-on"
                } else {
                    "ru/conversation/formatting-off"
                },
            )
        fun researchContent(data: String): String = resources.prompt(
            "ru/research/context",
            mapOf(
                "value0" to research.status,
                "value1" to data,
                "value2" to
                    resources.prompt(
                        if (links.visible(rule, history)) {
                            "ru/research/links-visible"
                        } else {
                            "ru/research/links-hidden"
                        },
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
        fun memoryContent(characters: Int): String = if (memory.isEmpty() || characters <= MEMORY_CONTEXT_OVERHEAD) {
            ""
        } else {
            resources.prompt(
                "ru/memory/context",
                mapOf(
                    "memory" to
                        Json.encodeToString(memory.limited(characters - MEMORY_CONTEXT_OVERHEAD)),
                ),
            )
        }
        val longTerm = memoryContent(memoryBudget)
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
        val latest = history.lastOrNull {
            !it.contextExcluded && !it.deleted && (it.text.isNotBlank() || images[it.id].orEmpty().isNotEmpty())
        }
        val fittedMemory = if (inputTarget == null || longTerm.isBlank()) {
            longTerm
        } else {
            val latestCost = history.stream().filter {
                !it.contextExcluded && !it.deleted && (it.id == latest?.id || images[it.id].orEmpty().isNotEmpty())
            }.mapToInt {
                tokens.estimate(
                    UserMessage.builder()
                        .text(
                            authors.content(
                                it,
                                if (it.id == latest?.id) {
                                    Int.MAX_VALUE
                                } else {
                                    MAX_MESSAGE_CHARACTERS
                                },
                            ),
                        )
                        .media(images[it.id].orEmpty()).build(),
                    inputTarget.model,
                    inputTarget.tokenEncoding,
                )
            }.sum()
            val available = budget.maximum(inputTarget) - reservedOutputTokens - latestCost -
                RECENT_HISTORY_RESERVE_TOKENS
            var low = 0
            var high = memoryBudget
            IntStream.range(0, MEMORY_BUDGET_SEARCH_STEPS).forEach {
                if (low < high) {
                    val middle = (low + high + 1) / 2
                    val text = listOf(baseSystem, memoryContent(middle), external, selectedStyle)
                        .filter(String::isNotBlank).joinToString("\n\n")
                    if (tokens.estimate(SystemMessage(text), inputTarget.model, inputTarget.tokenEncoding) <=
                        available
                    ) {
                        low = middle
                    } else {
                        high = middle - 1
                    }
                }
            }
            memoryContent(low)
        }
        val system = listOf(
            baseSystem,
            fittedMemory,
            external,
            selectedStyle,
        ).filter(String::isNotBlank).joinToString("\n\n")
        val candidates = history.asReversed().stream()
            .filter { !it.contextExcluded && !it.deleted }
            .filter { it.text.isNotBlank() || images[it.id].orEmpty().isNotEmpty() }
            .map { message ->
                val limit = if (message.id == latest?.id) {
                    Int.MAX_VALUE
                } else {
                    MAX_MESSAGE_CHARACTERS
                }
                UserMessage.builder().text(authors.content(message, limit)).media(images[message.id].orEmpty()).build()
            }.collect(Collectors.toList()).asReversed()

        var characters = 0
        var selectedMessages = 0
        val contextBudget = minOf(MAX_CONTEXT_CHARACTERS, MAX_PROMPT_CHARACTERS - system.length)
        val selected = candidates.asReversed().stream().filter {
            val latestMessage = selectedMessages++ == 0
            val media = it.media.isNotEmpty()
            characters += it.toString().length
            media || latestMessage || characters <= contextBudget
        }.collect(Collectors.toList()).asReversed()

        val context = MessageWindowChatMemory.builder().maxMessages(rule.contextMessages + images.size + 1).build()
        val conversationId = history.lastOrNull()?.chatId?.ifBlank { "preview" } ?: "preview"
        context.add(conversationId, listOf(SystemMessage(system)) + selected)
        return context.get(conversationId)
    }
}
