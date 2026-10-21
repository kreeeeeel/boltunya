package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class WeatherServiceUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEATHER_SERVICE_UNAVAILABLE, emptyMap(), cause)
