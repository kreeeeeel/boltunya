package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ReplyDeliveryFailedException : AppException(ErrorCode.REPLY_DELIVERY_FAILED)
