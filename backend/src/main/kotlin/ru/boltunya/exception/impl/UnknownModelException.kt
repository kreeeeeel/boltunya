package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class UnknownModelException : InvalidInputException(ErrorCode.UNKNOWN_MODEL)
