package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class RouteNotFoundException : AppException(ErrorCode.ROUTE_NOT_FOUND)
