package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class SpeechInstallFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.SPEECH_INSTALL_FAILED, cause = cause)
