package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class MalformedResearchUrlException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.MALFORMED_RESEARCH_URL, emptyMap(), cause)
