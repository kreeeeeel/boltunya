package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ResearchUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.RESEARCH_UNAVAILABLE, emptyMap(), cause)
