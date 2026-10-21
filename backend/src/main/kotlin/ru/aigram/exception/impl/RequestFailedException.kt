package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class RequestFailedException : AppException(ErrorCode.REQUEST_FAILED)
