package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class InvalidFieldLengthException(key: String, maximum: Int) :
    InvalidInputException(ErrorCode.INVALID_FIELD_LENGTH, mapOf("key" to key, "maximum" to maximum))
