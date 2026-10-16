package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class EngineUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.ENGINE_UNAVAILABLE, emptyMap(), cause)
