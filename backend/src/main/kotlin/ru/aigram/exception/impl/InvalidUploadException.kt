package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class InvalidUploadException : AppException(ErrorCode.INVALID_UPLOAD)
