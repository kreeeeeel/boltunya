package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class SpeechDraftUnavailableException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.SPEECH_DRAFT_UNAVAILABLE, cause = cause)
