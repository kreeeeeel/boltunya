package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class InvalidContextSelectionException : AppException(ErrorCode.INVALID_CONTEXT_SELECTION)
