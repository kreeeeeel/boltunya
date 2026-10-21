package ru.aigram.automation

import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ChatRule
import ru.aigram.domain.chat.DeliveryMode
import ru.aigram.storage.SettingsStore

@Component
class ReplyDeliveryPolicy(private val settings: SettingsStore) {
    fun replyTo(rule: ChatRule, sourceId: String): String {
        val mode = if (rule.deliveryMode == DeliveryMode.INHERIT) {
            settings.get().deliveryMode
        } else {
            rule.deliveryMode
        }
        return if (mode == DeliveryMode.SEND) {
            ""
        } else {
            sourceId
        }
    }
}
