package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ModelProfileDisabledException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_PROFILE_DISABLED, cause = cause)
