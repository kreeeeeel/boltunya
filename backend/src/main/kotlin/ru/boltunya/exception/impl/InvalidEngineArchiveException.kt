package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class InvalidEngineArchiveException : AppException(ErrorCode.INVALID_ENGINE_ARCHIVE)
