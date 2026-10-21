package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class UnsupportedModelException : InvalidInputException(ErrorCode.UNSUPPORTED_MODEL)
