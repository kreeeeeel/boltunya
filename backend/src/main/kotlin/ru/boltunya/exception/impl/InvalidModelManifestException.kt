package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class InvalidModelManifestException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INVALID_MODEL_MANIFEST, emptyMap(), cause)
