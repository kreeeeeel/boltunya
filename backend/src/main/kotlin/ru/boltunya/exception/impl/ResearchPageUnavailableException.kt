package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ResearchPageUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.RESEARCH_PAGE_UNAVAILABLE, emptyMap(), cause)
