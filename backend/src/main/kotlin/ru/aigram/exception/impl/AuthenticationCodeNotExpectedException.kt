package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class AuthenticationCodeNotExpectedException : InvalidInputException(ErrorCode.AUTHENTICATION_CODE_NOT_EXPECTED)
