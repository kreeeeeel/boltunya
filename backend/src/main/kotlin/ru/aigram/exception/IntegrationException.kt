package ru.aigram.exception

abstract class IntegrationException protected constructor(
    code: ErrorCode,
    parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : AppException(code, parameters, cause)
