package ru.boltunya.ai

import jakarta.inject.Inject
import ru.boltunya.domain.ChatMessage
import ru.boltunya.resources.ResourceCatalog
import java.util.Locale

private val resources = ResourceCatalog()

class ReplyQualityGuard @Inject constructor() {
    fun needsRetry(
        reply: String,
        history: List<ChatMessage>,
        instructions: String = "",
        styleHistory: List<ChatMessage> = history,
    ): Boolean {
        val answer = normalize(reply)
        val latestIncoming = normalize(history.lastOrNull { !it.outgoing }?.text.orEmpty())
        val han = resources.regex("common/ReplyQualityGuard-1")
        val languageRequested = resources.regex("intent/translation-request")
            .containsMatchIn(latestIncoming + " " + normalize(instructions)) || han.containsMatchIn(instructions)
        val unexpectedLanguage = resources.regex("common/ReplyQualityGuard-3").containsMatchIn(latestIncoming) &&
            han.containsMatchIn(reply) && !languageRequested &&
            history.none { !it.outgoing && han.containsMatchIn(it.text) }
        if (unexpectedLanguage) {
            return true
        }
        val identityQuestion = resources.regex("intent/identity-question")
            .containsMatchIn(latestIncoming)
        val helpRequested = resources.regex("intent/help-request").containsMatchIn(latestIncoming)
        val serviceTail = resources.regex("intent/service-tail")
            .containsMatchIn(answer)
        val boilerplate = (!helpRequested && serviceTail) ||
            (!identityQuestion && resources.regex("intent/identity-boilerplate").containsMatchIn(answer))
        if (boilerplate) {
            return true
        }
        val incoming = history.filter { !it.outgoing }.takeLast(2)
        if (incoming.size == 2 && normalize(incoming[0].text) == latestIncoming) {
            return false
        }
        if (answer.length >= 8 && answer.contains(' ') && styleHistory.stream().anyMatch {
                it.outgoing && normalize(it.text) == answer
            }
        ) {
            return true
        }
        val previous = history.asReversed().firstOrNull { it.outgoing && it.kind == "text" }?.text ?: return false
        val lastAnswer = normalize(previous)
        if (answer.length < 20 || lastAnswer.length < 20) {
            return false
        }
        val currentShingles = shingles(answer)
        val previousShingles = shingles(lastAnswer)
        val shared = currentShingles.intersect(previousShingles).size
        return answer == lastAnswer ||
            shared >= 3 && shared.toDouble() / minOf(currentShingles.size, previousShingles.size) > 0.65
    }

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace(resources.regex("common/ReplyQualityGuard-7"), " ").trim()

    private fun shingles(text: String): Set<String> = text.split(' ').windowed(3).map { it.joinToString(" ") }.toSet()
}
