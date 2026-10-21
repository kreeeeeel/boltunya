package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

private const val DEFAULT_RETRY_DELAY_MILLIS = 100L

class BackgroundInferenceDeferredException(val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS) :
    AppException(ErrorCode.BACKGROUND_INFERENCE_DEFERRED)
