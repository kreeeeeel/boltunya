package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ModelProviderFailedException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_PROVIDER_FAILED, cause = cause)
