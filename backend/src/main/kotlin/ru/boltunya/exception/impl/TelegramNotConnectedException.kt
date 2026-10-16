package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class TelegramNotConnectedException : InvalidInputException(ErrorCode.TELEGRAM_NOT_CONNECTED)
