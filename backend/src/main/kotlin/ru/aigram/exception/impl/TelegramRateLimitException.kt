package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class TelegramRateLimitException(val retryAfterSeconds: Long, cause: Throwable? = null) :
    AppException(ErrorCode.TELEGRAM_RATE_LIMIT, mapOf("retryAfterSeconds" to retryAfterSeconds), cause)
