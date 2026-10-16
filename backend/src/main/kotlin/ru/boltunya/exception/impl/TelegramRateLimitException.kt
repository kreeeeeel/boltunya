package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class TelegramRateLimitException(val retryAfterSeconds: Long, cause: Throwable? = null) :
    AppException(ErrorCode.TELEGRAM_RATE_LIMIT, mapOf("retryAfterSeconds" to retryAfterSeconds), cause)
