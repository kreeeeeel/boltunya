package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class BackgroundInferenceDeferredException : AppException(ErrorCode.BACKGROUND_INFERENCE_DEFERRED)
