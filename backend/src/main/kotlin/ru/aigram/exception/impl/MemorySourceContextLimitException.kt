package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MemorySourceContextLimitException : AppException(ErrorCode.MEMORY_SOURCE_CONTEXT_LIMIT)
