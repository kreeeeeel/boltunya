package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class MemoryConflictException : AppException(ErrorCode.MEMORY_CONFLICT)
