package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class TelegramDeleteFailedException : AppException(ErrorCode.TELEGRAM_DELETE_FAILED)
