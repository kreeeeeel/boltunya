package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ModelDownloadHttpFailedException(statusCode: Int, cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_DOWNLOAD_HTTP_FAILED, mapOf("statusCode" to statusCode), cause)
