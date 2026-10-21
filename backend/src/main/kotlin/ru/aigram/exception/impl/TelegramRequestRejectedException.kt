package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class TelegramRequestRejectedException : AppException(ErrorCode.TELEGRAM_REQUEST_REJECTED)
