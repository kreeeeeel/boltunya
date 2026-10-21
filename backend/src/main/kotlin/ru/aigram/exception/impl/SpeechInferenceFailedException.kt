package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class SpeechInferenceFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.SPEECH_INFERENCE_FAILED, cause = cause)
