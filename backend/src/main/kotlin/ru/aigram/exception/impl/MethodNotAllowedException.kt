package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MethodNotAllowedException : AppException(ErrorCode.METHOD_NOT_ALLOWED)
