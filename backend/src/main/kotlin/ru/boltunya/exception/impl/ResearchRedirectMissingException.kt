package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ResearchRedirectMissingException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.RESEARCH_REDIRECT_MISSING, emptyMap(), cause)
