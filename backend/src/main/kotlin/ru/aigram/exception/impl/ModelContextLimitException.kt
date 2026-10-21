package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelContextLimitException(requiredTokens: Int, contextLimit: Int) :
    AppException(
        ErrorCode.MODEL_CONTEXT_LIMIT,
        mapOf("requiredTokens" to requiredTokens, "contextLimit" to contextLimit),
    )
