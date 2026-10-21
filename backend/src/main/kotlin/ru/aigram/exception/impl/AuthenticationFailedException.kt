package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class AuthenticationFailedException : AppException(ErrorCode.AUTHENTICATION_FAILED)
