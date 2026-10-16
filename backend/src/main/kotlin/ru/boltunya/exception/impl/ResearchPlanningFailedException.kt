package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class ResearchPlanningFailedException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.RESEARCH_PLANNING_FAILED, emptyMap(), cause)
