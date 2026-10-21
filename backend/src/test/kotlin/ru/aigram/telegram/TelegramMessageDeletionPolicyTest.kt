package ru.aigram.telegram

import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import ru.aigram.domain.MessageDeletionOptions
import ru.aigram.domain.MessageDeletionScope
import ru.aigram.exception.impl.InvalidHistoryIdentifierException
import ru.aigram.exception.impl.MessageDeletionNotAllowedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramMessageDeletionPolicyTest {
    private val policy = TelegramMessageDeletionPolicy()

    @Test
    fun `uses exact Telegram IDs and explicit revoke without expanding self deletion to everyone`() {
        val options = MessageDeletionOptions(canDeleteForSelf = true, canDeleteForEveryone = true)
        val self = policy.deletionRequest(-100123, "987654321098765", MessageDeletionScope.SELF, options)
        assertEquals(-100123L, self.chatId)
        assertEquals(listOf(987654321098765L), self.messageIds.toList())
        assertFalse(self.revoke)
        assertTrue(policy.deletionRequest(-100123, "9", MessageDeletionScope.EVERYONE, options).revoke)
        val everyoneOnly = MessageDeletionOptions(canDeleteForSelf = false, canDeleteForEveryone = true)
        assertFailsWith<MessageDeletionNotAllowedException> {
            policy.deletionRequest(-100123, "9", MessageDeletionScope.SELF, everyoneOnly)
        }
        assertTrue(policy.deletionRequest(-100123, "9", MessageDeletionScope.EVERYONE, everyoneOnly).revoke)
    }

    @Test
    fun `preserves each independent Telegram permission and rejects invalid or unsupported operations`() {
        val permissions = policy.options(
            TdApi.MessageProperties().apply {
                canBeDeletedOnlyForSelf = true
                canBeDeletedForAllUsers = false
            },
        )
        assertTrue(permissions.canDeleteForSelf)
        assertFalse(permissions.canDeleteForEveryone)
        assertFailsWith<MessageDeletionNotAllowedException> {
            policy.deletionRequest(42, "1", MessageDeletionScope.EVERYONE, permissions)
        }
        listOf("0", "-1", "01", "+1", " 1", "99999999999999999999", "../../secret").stream().forEach {
            assertFailsWith<InvalidHistoryIdentifierException> { policy.propertiesRequest(42, it) }
        }
    }
}
