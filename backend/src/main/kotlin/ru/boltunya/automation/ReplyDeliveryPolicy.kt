package ru.boltunya.automation

import jakarta.inject.Inject
import ru.boltunya.domain.ChatRule
import ru.boltunya.domain.DeliveryMode
import ru.boltunya.storage.SettingsStore

class ReplyDeliveryPolicy @Inject constructor(private val settings: SettingsStore) {
    fun replyTo(rule: ChatRule, sourceId: String): String {
        val mode = if (rule.deliveryMode == DeliveryMode.INHERIT) settings.get().deliveryMode else rule.deliveryMode
        return if (mode == DeliveryMode.SEND) "" else sourceId
    }
}
