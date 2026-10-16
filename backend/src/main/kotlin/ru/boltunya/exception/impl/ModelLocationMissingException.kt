package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ModelLocationMissingException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_LOCATION_MISSING, emptyMap(), cause)
