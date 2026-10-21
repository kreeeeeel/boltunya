package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class InvalidMessageEditException : InvalidInputException(ErrorCode.INVALID_MESSAGE_EDIT)
