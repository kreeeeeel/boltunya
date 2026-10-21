package ru.aigram.telegram

import jakarta.inject.Inject
import ru.aigram.domain.TelegramStatus
import ru.aigram.storage.CredentialStore

class TelegramConnectionState @Inject constructor(credentials: CredentialStore) {
    @Volatile var status = TelegramStatus(state = if (credentials.exists()) "connecting" else "disconnected")

    @Volatile var version = 0L
}
