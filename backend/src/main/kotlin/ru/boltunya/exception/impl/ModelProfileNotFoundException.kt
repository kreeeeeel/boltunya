package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ModelProfileNotFoundException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_PROFILE_NOT_FOUND, cause = cause)
