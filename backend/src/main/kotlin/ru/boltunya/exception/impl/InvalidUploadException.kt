package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class InvalidUploadException : AppException(ErrorCode.INVALID_UPLOAD)
