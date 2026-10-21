package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class RegistryRedirectException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.REGISTRY_REDIRECT, emptyMap(), cause)
