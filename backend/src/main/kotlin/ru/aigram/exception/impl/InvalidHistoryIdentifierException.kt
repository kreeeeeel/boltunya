package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class InvalidHistoryIdentifierException : AppException(ErrorCode.INVALID_HISTORY_IDENTIFIER)
