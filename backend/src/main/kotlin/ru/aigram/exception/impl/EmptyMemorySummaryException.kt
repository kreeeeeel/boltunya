package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class EmptyMemorySummaryException : AppException(ErrorCode.EMPTY_MEMORY_SUMMARY)
