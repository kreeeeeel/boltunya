package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class TelegramEditFailedException(cause: Throwable? = null) :
    AppException(ErrorCode.TELEGRAM_EDIT_FAILED, cause = cause)
