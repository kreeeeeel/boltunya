package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ForbiddenHostException : AppException(ErrorCode.FORBIDDEN_HOST)
