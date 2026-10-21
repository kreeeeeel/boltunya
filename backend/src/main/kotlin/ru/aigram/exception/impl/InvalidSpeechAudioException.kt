package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class InvalidSpeechAudioException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.SPEECH_AUDIO_INVALID, cause = cause)
