package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class MessageEditTooLongException(maximum: Int) :
    InvalidInputException(ErrorCode.MESSAGE_EDIT_TOO_LONG, mapOf("maximum" to maximum))
