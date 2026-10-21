package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelTestConfirmationRequiredException(cause: Throwable? = null) :
    AppException(ErrorCode.MODEL_TEST_CONFIRMATION_REQUIRED, cause = cause)
