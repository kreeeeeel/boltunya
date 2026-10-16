package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class WebSearchUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEB_SEARCH_UNAVAILABLE, emptyMap(), cause)
