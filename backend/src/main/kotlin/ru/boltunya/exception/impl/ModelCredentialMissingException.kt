package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ModelCredentialMissingException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_CREDENTIAL_MISSING, cause = cause)
