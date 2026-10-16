package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class InvalidMediaIdentifierException : InvalidInputException(ErrorCode.INVALID_MEDIA_IDENTIFIER)
