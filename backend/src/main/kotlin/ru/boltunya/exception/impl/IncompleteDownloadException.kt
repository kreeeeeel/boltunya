package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class IncompleteDownloadException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INCOMPLETE_DOWNLOAD, emptyMap(), cause)
