package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class InvalidContextSelectionException : AppException(ErrorCode.INVALID_CONTEXT_SELECTION)
