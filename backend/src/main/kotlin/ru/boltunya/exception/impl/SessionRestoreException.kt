package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class SessionRestoreException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.SESSION_RESTORE, emptyMap(), cause)
