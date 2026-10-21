package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class InvalidResearchPlanException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INVALID_RESEARCH_PLAN, emptyMap(), cause)
