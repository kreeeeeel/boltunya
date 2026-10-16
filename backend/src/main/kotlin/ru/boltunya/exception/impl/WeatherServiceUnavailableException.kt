package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class WeatherServiceUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEATHER_SERVICE_UNAVAILABLE, emptyMap(), cause)
