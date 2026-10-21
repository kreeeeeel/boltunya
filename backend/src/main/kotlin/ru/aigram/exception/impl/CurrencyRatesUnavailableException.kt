package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class CurrencyRatesUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.CURRENCY_RATES_UNAVAILABLE, emptyMap(), cause)
