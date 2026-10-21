package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class SpeechUnsupportedPlatformException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.SPEECH_UNSUPPORTED_PLATFORM, cause = cause)
