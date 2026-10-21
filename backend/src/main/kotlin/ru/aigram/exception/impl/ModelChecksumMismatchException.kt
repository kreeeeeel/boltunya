package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ModelChecksumMismatchException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_CHECKSUM_MISMATCH, emptyMap(), cause)
