package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class InvalidMemoryChatException : AppException(ErrorCode.INVALID_MEMORY_CHAT)
