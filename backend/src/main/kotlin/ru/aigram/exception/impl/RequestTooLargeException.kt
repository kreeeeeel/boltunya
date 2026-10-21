package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class RequestTooLargeException : InvalidInputException(ErrorCode.REQUEST_TOO_LARGE)
