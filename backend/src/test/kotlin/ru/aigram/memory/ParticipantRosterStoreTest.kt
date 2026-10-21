package ru.aigram.memory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatMessage
import ru.aigram.domain.chat.ContextExclusionUpdate
import ru.aigram.domain.memory.MemoryContext
import ru.aigram.domain.memory.ParticipantIdentity
import ru.aigram.exception.impl.InvalidMemoryChatException
import ru.aigram.storage.ContextExclusionStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.IntStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParticipantRosterStoreTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `stores only group author identities and isolates accounts and conversations`() {
        val roster = ParticipantRosterStore(directory)
        val group = message(1, "Anna", "@anna_example")
        roster.record("100", "42", listOf(group, message(2).copy(group = false), message(3).copy(deleted = true)))
        assertEquals(listOf(ParticipantIdentity("user:1", "Anna", "anna_example")), roster.context("100", "42"))
        assertTrue(roster.context("101", "42").isEmpty())
        assertTrue(roster.context("100", "43").isEmpty())
        val stored = Files.readString(directory.resolve("memory/100/42/PARTICIPANTS.json"))
        assertFalse(stored.contains(group.text))
        assertFalse(stored.contains("personality"))
        assertEquals(roster.context("100", "42"), ParticipantRosterStore(directory).context("100", "42"))
        assertFailsWith<InvalidMemoryChatException> { roster.context("../outside", "42") }
        assertFailsWith<InvalidMemoryChatException> { roster.record("100", "../outside", listOf(group)) }
    }

    @Test
    fun `backfill does not overwrite newer identities and repeated cached messages refresh changed usernames`() {
        val roster = ParticipantRosterStore(directory)
        val newest = message(20, "New name", "new_username").copy(senderId = "user:1")
        roster.record("100", "42", listOf(newest))
        roster.record("100", "42", listOf(message(1, "Old name", "old_username")))
        assertEquals("New name", roster.context("100", "42").single().name)
        roster.record("100", "42", listOf(newest.copy(senderUsername = "updated_username")))
        assertEquals("updated_username", roster.context("100", "42").single().username)
        val firstOrder = ParticipantRosterStore(directory.resolve("first"))
        val reverseOrder = ParticipantRosterStore(directory.resolve("reverse"))
        val history = listOf(message(1), message(20).copy(senderId = "user:1"), message(2))
        firstOrder.record("100", "42", history)
        reverseOrder.record("100", "42", history.asReversed())
        assertEquals(firstOrder.context("100", "42"), reverseOrder.context("100", "42"))
    }

    @Test
    fun `caps retained participants and serializes a bounded prompt roster`() {
        val roster = ParticipantRosterStore(directory)
        val history = IntStream.rangeClosed(1, 1001).mapToObj { message(it) }.toList()
        roster.record("100", "42", history)
        val persisted = Json.parseToJsonElement(
            Files.readString(directory.resolve("memory/100/42/PARTICIPANTS.json")),
        ).jsonArray
        assertEquals(1000, persisted.size)
        assertFalse(persisted.any { it.toString().contains("\"id\":\"user:1\"") })
        val context = roster.context("100", "42")
        assertTrue(context.size <= 80)
        assertEquals("user:1001", context.first().id)
        assertTrue(Json.encodeToString(context).length <= 4000)
        val limited = MemoryContext(
            globalNotes = "owner notes ".repeat(300),
            chatNotes = "chat notes ".repeat(300),
            summary = "chat facts ".repeat(300),
            ownerSummary = "owner facts ".repeat(300),
            participants = context,
        ).limited(3000)
        val characters = limited.globalNotes.length + limited.chatNotes.length + limited.summary.length +
            limited.ownerSummary.length + Json.encodeToString(limited.participants).length
        assertTrue(characters <= 3000)
        assertTrue(limited.participants.isNotEmpty())
        assertTrue(MemoryContext(participants = context).limited(0).isEmpty())
    }

    @Test
    fun `exclusion policy hides old identities and allows rebuilding them from an included message`() {
        val exclusions = ContextExclusionStore(directory)
        val roster = ParticipantRosterStore(directory, exclusions)
        roster.record("100", "42", listOf(message(20).copy(senderId = "user:1")))
        exclusions.update("100", "42", "20", ContextExclusionUpdate())
        assertTrue(roster.context("100", "42").isEmpty())
        roster.record("100", "42", listOf(message(1)))
        assertEquals(listOf("user:1"), roster.context("100", "42").map { it.id })
        roster.record("100", "42", listOf(message(2).copy(contextExcluded = true)))
        assertEquals(1, roster.context("100", "42").size)
    }

    private fun message(id: Int, name: String = "User $id", username: String = "user_$id") = ChatMessage(
        "$id", "42", "Secret content not a participant fact", false, id.toLong(), group = true,
        senderName = name, senderId = "user:$id", senderUsername = username,
    )
}
