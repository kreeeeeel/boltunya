package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class InvalidSearchKeyException : IntegrationException(ErrorCode.INVALID_SEARCH_KEY)
