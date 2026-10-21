package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class WebSearchUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEB_SEARCH_UNAVAILABLE, emptyMap(), cause)
