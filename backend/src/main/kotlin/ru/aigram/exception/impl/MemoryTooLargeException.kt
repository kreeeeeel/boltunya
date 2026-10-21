package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MemoryTooLargeException : AppException(ErrorCode.MEMORY_TOO_LARGE)
