package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ModelRegistryUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_REGISTRY_UNAVAILABLE, emptyMap(), cause)
