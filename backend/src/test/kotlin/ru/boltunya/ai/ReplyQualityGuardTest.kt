package ru.boltunya.ai

import org.junit.jupiter.api.Test
import ru.boltunya.domain.ChatMessage
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplyQualityGuardTest {
    @Test
    fun `verified fact updates may share phrasing while exact copies and boilerplate still retry`() {
        val previous = "83.76 ₽ по ЦБ на 16.10.2026"
        val history = listOf(
            ChatMessage("1", "42", previous, true, 0, automated = true),
            ChatMessage("2", "42", "А евро?", false, 0),
        )
        val updated = "93.7693 ₽ по ЦБ на 16.10.2026"
        assertTrue(guard.needsRetry(updated, history))
        assertFalse(guard.needsRetry(updated, history, compareSimilarReplies = false))
        assertTrue(guard.needsRetry(previous, history, compareSimilarReplies = false))
        assertTrue(guard.needsRetry("Я — ИИ-помощник", history, compareSimilarReplies = false))
    }

    @Test
    fun `retries verbatim copying of multiword owner samples instead of answering the latest message`() {
        val sample = ChatMessage("1", "42", "ща гляну))", true, 0)
        val incoming = ChatMessage("2", "42", "Мне плохо, поговори со мной", false, 0)
        assertTrue(ReplyQualityGuard().needsRetry(sample.text, listOf(incoming), styleHistory = listOf(sample)))
        assertFalse(ReplyQualityGuard().needsRetry("да, рассказывай", listOf(incoming), styleHistory = listOf(sample)))
    }
    private val guard = ReplyQualityGuard()

    @Test
    fun `rejects unsolicited assistant boilerplate and repeated long phrases`() {
        val history = listOf(
            ChatMessage("1", "1", "Воу, я напишу тебе что-то интересное", true, 0),
            ChatMessage("2", "1", "Ахахха", false, 0),
        )
        assertTrue(guard.needsRetry("Ахахха, я напишу тебе что-то интересное", history))
        assertTrue(guard.needsRetry("Я — ИИ-помощник, у меня нет личных отношений", history))
        assertTrue(guard.needsRetry("21:30. Готов помочь чем-то еще?", history))
        assertFalse(guard.needsRetry("Что тебя так рассмешило?)", history))
    }

    @Test
    fun `allows offering help when explicitly requested`() {
        val history = listOf(ChatMessage("1", "1", "Можешь помочь?", false, 0))
        assertFalse(guard.needsRetry("Да, чем могу помочь?", history))
    }

    @Test
    fun `allows honest answers when asked whether this is AI`() {
        val history = listOf(ChatMessage("1", "1", "Ты бот?", false, 0))
        assertFalse(guard.needsRetry("Да, я ИИ-помощник владельца", history))
    }

    @Test
    fun `rejects accidental script changes but permits translation and explicit language instructions`() {
        val history = listOf(ChatMessage("1", "1", "Ахахха", false, 0))
        assertTrue(guard.needsRetry("Рада, что ты笑了", history))
        assertFalse(guard.needsRetry("笑了", history, "Отвечай по-китайски"))
        val translation = listOf(ChatMessage("1", "1", "Переведи слово привет на китайский", false, 0))
        assertFalse(guard.needsRetry("你好", translation))
    }
}
