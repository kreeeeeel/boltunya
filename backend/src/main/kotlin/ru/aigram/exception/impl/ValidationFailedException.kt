package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class ValidationFailedException : InvalidInputException(ErrorCode.VALIDATION_FAILED)
