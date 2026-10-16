package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class MethodNotAllowedException : AppException(ErrorCode.METHOD_NOT_ALLOWED)
