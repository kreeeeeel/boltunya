package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class TelegramSendFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.TELEGRAM_SEND_FAILED, emptyMap(), cause)
