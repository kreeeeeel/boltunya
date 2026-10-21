package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class SessionRequiredException : AppException(ErrorCode.SESSION_REQUIRED)
