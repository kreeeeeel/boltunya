package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class CrossOriginRejectedException : AppException(ErrorCode.CROSS_ORIGIN_REJECTED)
