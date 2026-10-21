package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class InvalidSpeechAssetException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.INVALID_SPEECH_ASSET, cause = cause)
