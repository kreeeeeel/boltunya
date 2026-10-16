package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class InvalidWeatherResponseException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INVALID_WEATHER_RESPONSE, emptyMap(), cause)
