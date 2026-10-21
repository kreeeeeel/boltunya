package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ModelWriteFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_WRITE_FAILED, emptyMap(), cause)
