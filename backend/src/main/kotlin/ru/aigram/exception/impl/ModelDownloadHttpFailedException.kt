package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ModelDownloadHttpFailedException(statusCode: Int, cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_DOWNLOAD_HTTP_FAILED, mapOf("statusCode" to statusCode), cause)
