package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ModelBlobDownloadFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_BLOB_DOWNLOAD_FAILED, emptyMap(), cause)
