package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class EmptyMemorySummaryException : AppException(ErrorCode.EMPTY_MEMORY_SUMMARY)
