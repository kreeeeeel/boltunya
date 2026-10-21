package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ModelBlobDownloadFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_BLOB_DOWNLOAD_FAILED, emptyMap(), cause)
