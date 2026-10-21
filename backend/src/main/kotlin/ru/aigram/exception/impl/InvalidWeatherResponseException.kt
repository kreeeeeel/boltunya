package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.IntegrationException

class InvalidWeatherResponseException(cause: Throwable? = null) :
    IntegrationException(ErrorCode.INVALID_WEATHER_RESPONSE, emptyMap(), cause)
