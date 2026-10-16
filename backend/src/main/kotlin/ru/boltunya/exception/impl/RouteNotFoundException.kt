package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class RouteNotFoundException : AppException(ErrorCode.ROUTE_NOT_FOUND)
