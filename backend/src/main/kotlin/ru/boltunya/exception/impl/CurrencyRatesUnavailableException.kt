package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class CurrencyRatesUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.CURRENCY_RATES_UNAVAILABLE, emptyMap(), cause)
