package ru.aigram.automation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.ChatRule
import ru.aigram.domain.DeliveryMode
import ru.aigram.storage.SettingsStore
import java.nio.file.Path
import kotlin.test.assertEquals

class ReplyDeliveryPolicyTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `defaults to replying and permits global or per chat standalone delivery`() {
        val settings = SettingsStore(directory)
        val policy = ReplyDeliveryPolicy(settings)
        assertEquals("123", policy.replyTo(ChatRule(), "123"))
        assertEquals("", policy.replyTo(ChatRule(deliveryMode = DeliveryMode.SEND), "123"))
        settings.update { it.copy(deliveryMode = DeliveryMode.SEND) }
        assertEquals("", policy.replyTo(ChatRule(), "123"))
        assertEquals("123", policy.replyTo(ChatRule(deliveryMode = DeliveryMode.REPLY), "123"))
    }
}
