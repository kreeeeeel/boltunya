package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class RegistryRedirectException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.REGISTRY_REDIRECT, emptyMap(), cause)
