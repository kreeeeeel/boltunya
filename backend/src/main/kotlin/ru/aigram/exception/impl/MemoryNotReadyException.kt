package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MemoryNotReadyException : AppException(ErrorCode.MEMORY_NOT_READY)
