package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class WeatherDateUnavailableException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEATHER_DATE_UNAVAILABLE, emptyMap(), cause)
