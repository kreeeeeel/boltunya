package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class ResearchPlanningFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.RESEARCH_PLANNING_FAILED, emptyMap(), cause)
