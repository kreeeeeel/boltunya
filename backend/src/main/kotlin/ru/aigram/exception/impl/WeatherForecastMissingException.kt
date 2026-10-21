package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class WeatherForecastMissingException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.WEATHER_FORECAST_MISSING, emptyMap(), cause)
