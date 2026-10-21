package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ModelRegistryUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MODEL_REGISTRY_UNAVAILABLE, emptyMap(), cause)
