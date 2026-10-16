package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class PrivateResearchAddressException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.PRIVATE_RESEARCH_ADDRESS, emptyMap(), cause)
