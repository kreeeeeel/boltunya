package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class InvalidSpeechRequestException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.INVALID_SPEECH_REQUEST, cause = cause)
