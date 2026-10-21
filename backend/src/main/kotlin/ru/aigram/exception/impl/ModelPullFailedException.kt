package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ModelPullFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_PULL_FAILED, emptyMap(), cause)
