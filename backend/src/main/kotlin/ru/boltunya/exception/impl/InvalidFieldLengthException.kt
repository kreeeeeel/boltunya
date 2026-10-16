package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class InvalidFieldLengthException(key: String, maximum: Int) :
    InvalidInputException(ErrorCode.INVALID_FIELD_LENGTH, mapOf("key" to key, "maximum" to maximum))
