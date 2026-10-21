package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ModelLocationMissingException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_LOCATION_MISSING, emptyMap(), cause)
