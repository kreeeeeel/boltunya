package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class EngineHttpFailedException(statusCode: Int, cause: Throwable? = null) :
    IntegrationException(ErrorCode.ENGINE_HTTP_FAILED, mapOf("statusCode" to statusCode), cause)
