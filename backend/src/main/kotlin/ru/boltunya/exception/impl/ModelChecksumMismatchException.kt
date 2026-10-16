package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ModelChecksumMismatchException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_CHECKSUM_MISMATCH, emptyMap(), cause)
