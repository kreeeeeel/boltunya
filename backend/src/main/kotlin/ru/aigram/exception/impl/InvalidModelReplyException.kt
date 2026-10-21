package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class InvalidModelReplyException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INVALID_MODEL_REPLY, emptyMap(), cause)
