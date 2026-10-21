package ru.aigram.exception

abstract class InvalidInputException protected constructor(
    code: ErrorCode,
    parameters: Map<String, Any> = emptyMap(),
    cause: Throwable? = null,
) : AppException(code, parameters, cause)
