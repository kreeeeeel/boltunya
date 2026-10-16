package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class InvalidResearchPlanException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INVALID_RESEARCH_PLAN, emptyMap(), cause)
