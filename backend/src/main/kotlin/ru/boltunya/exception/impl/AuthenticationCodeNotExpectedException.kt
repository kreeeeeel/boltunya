package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class AuthenticationCodeNotExpectedException : InvalidInputException(ErrorCode.AUTHENTICATION_CODE_NOT_EXPECTED)
