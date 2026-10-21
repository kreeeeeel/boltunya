package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class InvalidSpeechModelImportException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.INVALID_SPEECH_MODEL_IMPORT, cause = cause)
