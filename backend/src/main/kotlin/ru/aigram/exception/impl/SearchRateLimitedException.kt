package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class SearchRateLimitedException : IntegrationException(ErrorCode.SEARCH_RATE_LIMITED)
