package ru.aigram.transformer.telegram

import ru.aigram.contract.dto.Credentials
import ru.aigram.contract.dto.Telegram
import ru.aigram.domain.telegram.TelegramCredentials
import ru.aigram.domain.telegram.TelegramStatus

fun Credentials.toDomain(): TelegramCredentials = TelegramCredentials(apiId, apiHash, phone)

fun TelegramStatus.toContract(): Telegram = Telegram(
    state = state,
    name = name,
    prompt = prompt,
    error = error,
    avatarUrl = avatarUrl,
    userId = userId,
)
