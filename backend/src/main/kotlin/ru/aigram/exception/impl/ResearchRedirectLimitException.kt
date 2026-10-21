package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ResearchRedirectLimitException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.RESEARCH_REDIRECT_LIMIT, emptyMap(), cause)
