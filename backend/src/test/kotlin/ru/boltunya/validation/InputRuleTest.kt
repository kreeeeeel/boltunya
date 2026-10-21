package ru.boltunya.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputRuleTest {
    @Test
    fun `accepts only bounded structural identifiers and safe storage keys`() {
        listOf("42", "-100123", Long.MIN_VALUE.toString(), Long.MAX_VALUE.toString()).stream().forEach {
            assertTrue(InputRule.CHAT_ID.accepts(it), it)
        }
        listOf("0", "01", "+1", "-0", "1/../../", "9223372036854775808", "１２", "1\n").stream().forEach {
            assertFalse(InputRule.CHAT_ID.accepts(it), it)
        }
        assertTrue(InputRule.STYLE_ID.accepts("last-message-1"))
        assertFalse(InputRule.STYLE_ID.accepts("../secret"))
        assertFalse(InputRule.STYLE_ID.accepts("x".repeat(65)))
        assertTrue(InputRule.CREDENTIALS_PHONE.accepts("+79991234567"))
        assertFalse(InputRule.CREDENTIALS_PHONE.accepts("+7999 1234567"))
    }

    @Test
    fun `requires canonical GPU UUIDs and exact lowercase registry digests`() {
        assertTrue(InputRule.GPU_UUID.accepts("GPU-12345678-1234-1234-abcd-123456789abc"))
        assertFalse(InputRule.GPU_UUID.accepts("GPU-1-2-3-4-5"))
        assertFalse(InputRule.GPU_UUID.accepts("GPU-12345678-1234-1234-abcd-123456789abc\n"))
        assertTrue(InputRule.MODEL_DIGEST.accepts("sha256:" + "a1".repeat(32)))
        assertFalse(InputRule.MODEL_DIGEST.accepts("sha256:" + "A1".repeat(32)))
        assertFalse(InputRule.MODEL_DIGEST.accepts("sha256:../secret"))
    }
}
