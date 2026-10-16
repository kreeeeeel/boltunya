package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class InvalidSearchKeyException : IntegrationException(ErrorCode.INVALID_SEARCH_KEY)
