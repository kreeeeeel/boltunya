package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class UnsupportedAuthenticationException : InvalidInputException(ErrorCode.UNSUPPORTED_AUTHENTICATION)
