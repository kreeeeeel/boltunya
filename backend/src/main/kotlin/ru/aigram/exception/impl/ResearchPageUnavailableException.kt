package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ResearchPageUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.RESEARCH_PAGE_UNAVAILABLE, emptyMap(), cause)
