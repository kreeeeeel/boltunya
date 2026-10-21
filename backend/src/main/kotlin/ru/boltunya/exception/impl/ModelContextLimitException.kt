package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ModelContextLimitException : AppException(ErrorCode.MODEL_CONTEXT_LIMIT)
