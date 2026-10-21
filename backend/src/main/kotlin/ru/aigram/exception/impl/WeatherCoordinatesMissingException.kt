package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class WeatherCoordinatesMissingException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEATHER_COORDINATES_MISSING, emptyMap(), cause)
