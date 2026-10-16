package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class WeatherCoordinatesMissingException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEATHER_COORDINATES_MISSING, emptyMap(), cause)
