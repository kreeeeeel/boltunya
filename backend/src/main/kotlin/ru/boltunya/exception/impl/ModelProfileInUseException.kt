package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ModelProfileInUseException(cause: Throwable? = null) : AppException(ErrorCode.MODEL_PROFILE_IN_USE, cause = cause)
