package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class InvalidModelDigestException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INVALID_MODEL_DIGEST, emptyMap(), cause)
