package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class SearchRateLimitedException : IntegrationException(ErrorCode.SEARCH_RATE_LIMITED)
