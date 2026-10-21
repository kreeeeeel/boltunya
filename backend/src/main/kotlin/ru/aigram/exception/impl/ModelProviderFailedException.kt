package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelProviderFailedException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_PROVIDER_FAILED, cause = cause)
