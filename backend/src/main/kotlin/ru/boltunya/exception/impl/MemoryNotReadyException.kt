package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class MemoryNotReadyException : AppException(ErrorCode.MEMORY_NOT_READY)
