package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class UnknownMediaException : InvalidInputException(ErrorCode.UNKNOWN_MEDIA)
