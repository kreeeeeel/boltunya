package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class SpeechAudioLimitException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.SPEECH_AUDIO_LIMIT, cause = cause)
