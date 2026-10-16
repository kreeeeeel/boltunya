package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class InvalidMemoryChatException : AppException(ErrorCode.INVALID_MEMORY_CHAT)
