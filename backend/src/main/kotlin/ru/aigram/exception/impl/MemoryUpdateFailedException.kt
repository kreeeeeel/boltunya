package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MemoryUpdateFailedException : AppException(ErrorCode.MEMORY_UPDATE_FAILED)
