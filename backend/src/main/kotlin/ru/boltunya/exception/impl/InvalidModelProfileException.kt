package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class InvalidModelProfileException(cause: Throwable? = null) :
    AppException(ErrorCode.INVALID_MODEL_PROFILE, cause = cause)
