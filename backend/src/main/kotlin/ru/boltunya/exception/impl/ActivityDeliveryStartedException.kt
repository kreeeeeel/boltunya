package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ActivityDeliveryStartedException : AppException(ErrorCode.ACTIVITY_DELIVERY_STARTED)
