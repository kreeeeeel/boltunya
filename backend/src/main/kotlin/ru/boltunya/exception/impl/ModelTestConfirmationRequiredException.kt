package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ModelTestConfirmationRequiredException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_TEST_CONFIRMATION_REQUIRED, cause = cause)
