package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelProviderRefusedException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_PROVIDER_REFUSED, cause = cause)
