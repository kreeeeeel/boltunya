package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ForbiddenHostException : AppException(ErrorCode.FORBIDDEN_HOST)
