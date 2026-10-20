package ru.boltunya.ai

import jakarta.inject.Inject
import ru.boltunya.domain.ChatMessage
import ru.boltunya.resources.ResourceCatalog
import java.util.Locale

private val resources = ResourceCatalog()
private const val MIN_STATEMENT_ECHO_CHARACTERS = 32

class ReplyQualityGuard @Inject constructor() {
    fun needsRetry(
        reply: String,
        history: List<ChatMessage>,
        instructions: String = "",
        styleHistory: List<ChatMessage> = history,
        compareSimilarReplies: Boolean = true,
    ): Boolean {
        if (containsInternalAnalysis(reply)) return true
        val answer = normalize(reply)
        val incomingText = history.lastOrNull { !it.outgoing }?.text.orEmpty()
        val latestIncoming = normalize(incomingText)
        val copiedQuestion = incomingText.contains('?') && reply.contains('?')
        if (answer == latestIncoming && answer.contains(' ') &&
            (copiedQuestion || answer.length >= MIN_STATEMENT_ECHO_CHARACTERS)
        ) {
            return true
        }
        val han = resources.regex("common/ReplyQualityGuard-1")
        val languageContext = latestIncoming + " " + normalize(instructions)
        val hanRequested = resources.regex("intent/translation-request").containsMatchIn(languageContext) ||
            han.containsMatchIn(instructions)
        val cyrillicRequested = resources.regex("intent/cyrillic-language-request").containsMatchIn(languageContext)
        val unexpectedLanguage = resources.regex("common/ReplyQualityGuard-3").containsMatchIn(latestIncoming) &&
            han.containsMatchIn(reply) && !hanRequested &&
            history.none { !it.outgoing && han.containsMatchIn(it.text) }
        if (unexpectedLanguage || !cyrillicRequested && mismatchedWritingSystem(reply, incomingText)) {
            return true
        }
        val identityQuestion = resources.regex("intent/identity-question")
            .containsMatchIn(latestIncoming)
        if (identityQuestion && resources.regex("intent/identity-human-claim").containsMatchIn(answer)) return true
        val helpRequested = resources.regex("intent/help-request").containsMatchIn(latestIncoming)
        val serviceTail = resources.regex("intent/service-tail")
            .containsMatchIn(answer)
        val boilerplate = (!helpRequested && serviceTail) ||
            (!identityQuestion && resources.regex("intent/identity-boilerplate").containsMatchIn(answer))
        if (boilerplate) {
            return true
        }
        val recalling = resources.regex("intent/recall-request").containsMatchIn(latestIncoming)
        val previous = history.asReversed().firstOrNull { it.outgoing && it.kind == "text" }?.text.orEmpty()
        val incoming = history.filter { !it.outgoing }.takeLast(2)
        val repeatedIncoming = incoming.size == 2 && normalize(incoming[0].text) == latestIncoming
        if (!((recalling || repeatedIncoming) && normalize(previous) == answer) &&
            answer.length >= 8 && answer.contains(' ') && styleHistory.stream().anyMatch {
                it.outgoing && normalize(it.text) == answer
            }
        ) {
            return true
        }
        if (recalling || repeatedIncoming || previous.isBlank()) return false
        val lastAnswer = normalize(previous)
        if (answer.length < 20 || lastAnswer.length < 20) {
            return false
        }
        val currentShingles = shingles(answer)
        val previousShingles = shingles(lastAnswer)
        val shared = currentShingles.intersect(previousShingles).size
        return answer == lastAnswer ||
            compareSimilarReplies && shared >= 3 &&
            shared.toDouble() / minOf(currentShingles.size, previousShingles.size) > 0.65
    }

    fun containsInternalAnalysis(reply: String): Boolean = resources.regex("intent/internal-analysis")
        .containsMatchIn(reply)

    private fun mismatchedWritingSystem(reply: String, incoming: String): Boolean {
        if (dominantWritingSystem(incoming.replace(resources.regex("common/research-url"), "")) !=
            Character.UnicodeScript.LATIN ||
            dominantWritingSystem(reply.replace(resources.regex("common/research-url"), "")) !=
            Character.UnicodeScript.CYRILLIC
        ) {
            return false
        }
        val words = reply.split(' ', '\n', '\t').filter { word -> word.any(Char::isLetter) }
        return words.size >= 2 && words.any { word -> word.firstOrNull(Char::isLetter)?.isLowerCase() == true }
    }

    private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace(resources.regex("common/ReplyQualityGuard-7"), " ").trim()

    private fun shingles(text: String): Set<String> = text.split(' ').windowed(3).map { it.joinToString(" ") }.toSet()
}
