package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class EmptyModelReplyException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.EMPTY_MODEL_REPLY, emptyMap(), cause)
