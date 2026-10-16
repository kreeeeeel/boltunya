package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class ValidationFailedException : InvalidInputException(ErrorCode.VALIDATION_FAILED)
