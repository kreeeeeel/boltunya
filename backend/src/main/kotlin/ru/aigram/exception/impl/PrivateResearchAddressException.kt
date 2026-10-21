package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class PrivateResearchAddressException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.PRIVATE_RESEARCH_ADDRESS, emptyMap(), cause)
