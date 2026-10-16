package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ModelPullFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_PULL_FAILED, emptyMap(), cause)
