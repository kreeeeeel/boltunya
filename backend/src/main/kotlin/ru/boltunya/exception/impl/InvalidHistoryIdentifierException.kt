package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class InvalidHistoryIdentifierException : AppException(ErrorCode.INVALID_HISTORY_IDENTIFIER)
