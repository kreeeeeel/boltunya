package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ActivityDeliveryStartedException : AppException(ErrorCode.ACTIVITY_DELIVERY_STARTED)
