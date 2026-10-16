package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.IntegrationException

class WeatherForecastMissingException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEATHER_FORECAST_MISSING, emptyMap(), cause)
