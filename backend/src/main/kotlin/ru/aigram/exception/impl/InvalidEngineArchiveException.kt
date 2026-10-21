package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class InvalidEngineArchiveException : AppException(ErrorCode.INVALID_ENGINE_ARCHIVE)
