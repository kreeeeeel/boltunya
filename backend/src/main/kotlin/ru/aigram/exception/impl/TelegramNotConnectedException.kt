package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class TelegramNotConnectedException : InvalidInputException(ErrorCode.TELEGRAM_NOT_CONNECTED)
