package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class WeatherDateUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEATHER_DATE_UNAVAILABLE, emptyMap(), cause)
