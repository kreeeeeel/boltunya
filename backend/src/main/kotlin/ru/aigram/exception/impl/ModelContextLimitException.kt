package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelContextLimitException : AppException(ErrorCode.MODEL_CONTEXT_LIMIT)
