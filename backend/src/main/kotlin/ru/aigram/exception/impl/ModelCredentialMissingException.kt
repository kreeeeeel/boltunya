package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelCredentialMissingException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_CREDENTIAL_MISSING, cause = cause)
