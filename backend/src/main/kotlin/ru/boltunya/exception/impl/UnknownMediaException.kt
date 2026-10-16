package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class UnknownMediaException : InvalidInputException(ErrorCode.UNKNOWN_MEDIA)
