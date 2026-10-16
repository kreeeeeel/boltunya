package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class CrossOriginRejectedException : AppException(ErrorCode.CROSS_ORIGIN_REJECTED)
