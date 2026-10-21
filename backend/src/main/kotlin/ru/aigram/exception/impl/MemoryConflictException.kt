package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MemoryConflictException : AppException(ErrorCode.MEMORY_CONFLICT)
