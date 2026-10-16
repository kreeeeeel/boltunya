package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class RepetitiveModelReplyException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.REPETITIVE_MODEL_REPLY, emptyMap(), cause)
