package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class SessionRestoreException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.SESSION_RESTORE, emptyMap(), cause)
