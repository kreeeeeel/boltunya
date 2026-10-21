package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class SpeechDraftConsumedException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.SPEECH_DRAFT_CONSUMED, cause = cause)
