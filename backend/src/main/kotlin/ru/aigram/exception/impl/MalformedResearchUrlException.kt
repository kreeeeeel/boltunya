package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class MalformedResearchUrlException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MALFORMED_RESEARCH_URL, emptyMap(), cause)
