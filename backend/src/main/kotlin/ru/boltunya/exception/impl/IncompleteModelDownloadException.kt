package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class IncompleteModelDownloadException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INCOMPLETE_MODEL_DOWNLOAD, emptyMap(), cause)
