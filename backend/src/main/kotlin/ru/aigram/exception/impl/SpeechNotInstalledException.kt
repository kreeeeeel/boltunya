package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class SpeechNotInstalledException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.SPEECH_NOT_INSTALLED, cause = cause)
