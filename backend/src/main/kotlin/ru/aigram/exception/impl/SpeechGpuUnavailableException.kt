package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class SpeechGpuUnavailableException(cause: Throwable? = null) :
    InvalidInputException(ErrorCode.SPEECH_GPU_UNAVAILABLE, cause = cause)
