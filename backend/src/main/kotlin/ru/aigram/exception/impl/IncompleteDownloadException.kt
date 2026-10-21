package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class IncompleteDownloadException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INCOMPLETE_DOWNLOAD, emptyMap(), cause)
