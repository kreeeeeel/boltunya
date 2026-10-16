package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class RequestFailedException : AppException(ErrorCode.REQUEST_FAILED)
