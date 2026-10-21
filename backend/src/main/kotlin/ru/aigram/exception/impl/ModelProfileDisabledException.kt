package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelProfileDisabledException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_PROFILE_DISABLED, cause = cause)
