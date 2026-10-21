package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class InvalidModelDigestException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INVALID_MODEL_DIGEST, emptyMap(), cause)
