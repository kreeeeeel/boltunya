package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ResearchUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.RESEARCH_UNAVAILABLE, emptyMap(), cause)
