package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class MemoryTooLargeException : AppException(ErrorCode.MEMORY_TOO_LARGE)
