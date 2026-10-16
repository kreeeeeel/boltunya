package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class MemoryUpdateFailedException : AppException(ErrorCode.MEMORY_UPDATE_FAILED)
