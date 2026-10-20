package ru.boltunya.ai

import org.junit.jupiter.api.Test
import ru.boltunya.domain.ChatMessage
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplyQualityGuardTest {
    @Test
    fun `retries false human identity claims only when asked about automation`() {
        val history = listOf(ChatMessage("1", "synthetic", "Ты бот или человек?", false, 0))
        listOf(
            "Это автоматический помощник владельца — я человек, но работаю как бот.",
            "I am a human.",
            "Я справжня людина.",
            "Мен нағыз адаммын.",
        ).stream().forEach { assertTrue(guard.needsRetry(it, history), it) }
        assertFalse(guard.needsRetry("Я не человек, отвечает помощник владельца", history))
        assertFalse(guard.needsRetry("Это автоматический помощник владельца", history))
        val ordinary = listOf(ChatMessage("1", "synthetic", "Как ты себя чувствуешь?", false, 0))
        assertFalse(guard.needsRetry("Я человек, иногда тоже устаю", ordinary))
    }

    @Test
    fun `rejects copying a question while allowing affirmative answers and mutual greetings`() {
        val question = listOf(ChatMessage("1", "synthetic", "hey, how is it going?", false, 0))
        assertTrue(guard.needsRetry("hey, how is it going?", question))
        assertFalse(guard.needsRetry("all good, how about you?", question))
        val confirmation = listOf(ChatMessage("1", "synthetic", "Всё хорошо?", false, 0))
        assertFalse(guard.needsRetry("Всё хорошо.", confirmation))
        val greeting = listOf(ChatMessage("1", "synthetic", "Привет!", false, 0))
        assertFalse(guard.needsRetry("Привет!", greeting))
    }

    @Test
    fun `allows recalling earlier facts without allowing internal analysis or assistant boilerplate`() {
        val earlier = "Встретимся в кафе Маяк в 19:30"
        val history = listOf(
            ChatMessage("1", "synthetic", earlier, true, 0),
            ChatMessage("2", "synthetic", "Ага. Напомни, где и во сколько?", false, 0),
        )
        assertFalse(guard.needsRetry(earlier, history))
        assertFalse(guard.needsRetry("В кафе Маяк в 19:30", history))
        val unrelatedSample = ChatMessage("style", "style", "ок, тогда вечером", true, 0)
        assertTrue(guard.needsRetry(unrelatedSample.text, history, styleHistory = listOf(unrelatedSample)))
        assertTrue(
            guard.needsRetry(
                unrelatedSample.text,
                history + history.last().copy(id = "3"),
                styleHistory = listOf(unrelatedSample),
            ),
        )
        assertTrue(guard.needsRetry("Владелец должен ответить в его стиле", history))
        assertTrue(guard.needsRetry("Я ИИ-помощник, могу помочь", history))
    }

    @Test
    fun `retries Cyrillic conversation replies to Latin messages while preserving names and requested translations`() {
        val history = listOf(ChatMessage("1", "synthetic", "hey, how is it going?", false, 0))
        assertTrue(guard.needsRetry("привет, всё отлично", history))
        assertFalse(guard.needsRetry("all good, how about you?", history))
        assertFalse(guard.needsRetry("Болтунья", history))
        assertFalse(guard.needsRetry("Александр Пушкин", history))
        assertFalse(guard.needsRetry("привет, всё отлично", history, "Отвечай по-русски"))
        assertTrue(guard.needsRetry("привет, всё отлично", history, "Отвечай по-английски"))
        val russian = listOf(ChatMessage("1", "synthetic", "Привет, как дела?", false, 0))
        assertTrue(guard.needsRetry("Рада, что ты笑了", russian, "Отвечай по-русски"))
        val link = listOf(
            ChatMessage("1", "synthetic", "Проверь ссылку https://example.org/a-long-english-address", false, 0),
        )
        assertFalse(guard.needsRetry("ссылка открывается", link))
    }

    @Test
    fun `rejects untagged internal analysis in supported languages without blocking normal planning conversation`() {
        val history = listOf(ChatMessage("1", "synthetic", "напиши рецепт сырников", false, 0))
        listOf(
            "Хорошо, мне нужно составить ответ от лица владельца Telegram в соответствии с заданными правилами.",
            "В прошлых сообщениях есть примеры в owner_style.",
            "Последнее сообщение собеседника — напиши рецепт сырников.",
            "I need to write a reply on behalf of the owner.",
            "Мені потрібно написати відповідь від імені власника.",
            "Сұхбаттасушының соңғы хабарламасы рецепт туралы.",
        ).stream().forEach { assertTrue(guard.needsRetry(it, history), it) }
        listOf(
            "сначала смешай творог с яйцом, потом добавь муку",
            "мне нужно написать сообщение маме, потом выйду",
            "да, сначала проверю расписание",
            "I need to write to my friend first",
            "мені потрібно написати мамі",
        ).stream().forEach { assertFalse(guard.containsInternalAnalysis(it), it) }
    }

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
    fun `retries a repeated refusal when the next incoming message asks an ordinary question`() {
        val previous = "Я против такого поступка, давай лучше сменим тему."
        val history = listOf(
            ChatMessage("1", "42", previous, true, 0, automated = true),
            ChatMessage("2", "42", "Как прошёл день?", false, 0),
        )
        assertTrue(guard.needsRetry(previous, history))
        assertFalse(guard.needsRetry("да нормально, спокойно", history))
    }

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
