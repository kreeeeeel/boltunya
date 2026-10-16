package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class BackgroundInferenceDeferredException : AppException(ErrorCode.BACKGROUND_INFERENCE_DEFERRED)
