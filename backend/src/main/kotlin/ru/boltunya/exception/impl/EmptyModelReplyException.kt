package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class EmptyModelReplyException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.EMPTY_MODEL_REPLY, emptyMap(), cause)
