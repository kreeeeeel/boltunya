package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelCapabilityUnavailableException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_CAPABILITY_UNAVAILABLE, cause = cause)
