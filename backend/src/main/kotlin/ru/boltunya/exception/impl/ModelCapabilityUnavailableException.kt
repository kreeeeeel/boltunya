package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ModelCapabilityUnavailableException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_CAPABILITY_UNAVAILABLE, cause = cause)
