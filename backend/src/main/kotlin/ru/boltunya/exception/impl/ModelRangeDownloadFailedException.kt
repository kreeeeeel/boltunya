package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ModelRangeDownloadFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_RANGE_DOWNLOAD_FAILED, emptyMap(), cause)
