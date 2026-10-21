package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class InvalidResearchUrlException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INVALID_RESEARCH_URL, emptyMap(), cause)
