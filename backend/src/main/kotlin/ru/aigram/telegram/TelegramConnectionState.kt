package ru.aigram.telegram

import org.springframework.stereotype.Component
import ru.aigram.domain.telegram.TelegramStatus
import ru.aigram.storage.CredentialStore

@Component
class TelegramConnectionState(credentials: CredentialStore) {
    @Volatile var status = TelegramStatus(
        state = if (credentials.exists()) {
            "connecting"
        } else {
            "disconnected"
        },
    )

    @Volatile var version = 0L
}
