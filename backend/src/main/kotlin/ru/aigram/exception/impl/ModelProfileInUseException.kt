package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelProfileInUseException(cause: Throwable? = null) : AppException(ErrorCode.MODEL_PROFILE_IN_USE, cause = cause)
