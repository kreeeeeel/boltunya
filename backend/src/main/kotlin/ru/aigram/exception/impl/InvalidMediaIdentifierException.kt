package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class InvalidMediaIdentifierException : InvalidInputException(ErrorCode.INVALID_MEDIA_IDENTIFIER)
