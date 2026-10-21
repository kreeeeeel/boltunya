package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class IncompleteModelDownloadException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INCOMPLETE_MODEL_DOWNLOAD, emptyMap(), cause)
