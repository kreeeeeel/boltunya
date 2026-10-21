package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class TelegramConnectionException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.TELEGRAM_CONNECTION, emptyMap(), cause)
