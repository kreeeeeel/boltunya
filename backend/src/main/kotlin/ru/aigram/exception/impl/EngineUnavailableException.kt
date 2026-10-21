package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class EngineUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.ENGINE_UNAVAILABLE, emptyMap(), cause)
