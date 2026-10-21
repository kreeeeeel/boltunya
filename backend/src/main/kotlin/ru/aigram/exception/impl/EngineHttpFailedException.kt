package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class EngineHttpFailedException(statusCode: Int, cause: Throwable? = null) :
    IntegrationException(ErrorCode.ENGINE_HTTP_FAILED, mapOf("statusCode" to statusCode), cause)
