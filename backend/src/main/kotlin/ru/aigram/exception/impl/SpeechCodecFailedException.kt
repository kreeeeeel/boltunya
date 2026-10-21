package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class SpeechCodecFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.SPEECH_CODEC_FAILED, cause = cause)
