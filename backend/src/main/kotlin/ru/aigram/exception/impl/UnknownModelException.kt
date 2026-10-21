package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class UnknownModelException : InvalidInputException(ErrorCode.UNKNOWN_MODEL)
