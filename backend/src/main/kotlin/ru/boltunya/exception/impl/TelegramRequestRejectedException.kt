package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class TelegramRequestRejectedException : AppException(ErrorCode.TELEGRAM_REQUEST_REJECTED)
