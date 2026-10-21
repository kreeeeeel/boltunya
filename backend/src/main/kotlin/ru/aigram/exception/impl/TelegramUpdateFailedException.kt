package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class TelegramUpdateFailedException : AppException(ErrorCode.TELEGRAM_UPDATE_FAILED)
