package ru.boltunya.telegram

import jakarta.inject.Inject
import ru.boltunya.domain.TelegramStatus
import ru.boltunya.storage.CredentialStore

class TelegramConnectionState @Inject constructor(credentials: CredentialStore) {
    @Volatile var status = TelegramStatus(state = if (credentials.exists()) "connecting" else "disconnected")

    @Volatile var version = 0L
}
