package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class SpeechTimeoutException(cause: Throwable? = null) : IntegrationException(ErrorCode.SPEECH_TIMEOUT, cause = cause)
