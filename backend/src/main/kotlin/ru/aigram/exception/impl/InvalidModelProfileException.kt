package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class InvalidModelProfileException(cause: Throwable? = null) :
    AppException(ErrorCode.INVALID_MODEL_PROFILE, cause = cause)
