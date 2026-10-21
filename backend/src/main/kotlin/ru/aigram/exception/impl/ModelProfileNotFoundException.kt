package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelProfileNotFoundException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_PROFILE_NOT_FOUND, cause = cause)
