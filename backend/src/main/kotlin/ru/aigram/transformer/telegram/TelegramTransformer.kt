package ru.aigram.transformer.telegram

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.Credentials
import ru.aigram.contract.dto.Telegram
import ru.aigram.domain.telegram.TelegramCredentials
import ru.aigram.domain.telegram.TelegramStatus

@Component
class TelegramTransformer {
    fun toDomain(value: Credentials): TelegramCredentials = TelegramCredentials(value.apiId, value.apiHash, value.phone)

    fun toContract(value: TelegramStatus): Telegram = Telegram(
        state = value.state,
        name = value.name,
        prompt = value.prompt,
        error = value.error,
        avatarUrl = value.avatarUrl,
        userId = value.userId,
    )
}
