package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ReplyDeliveryFailedException : AppException(ErrorCode.REPLY_DELIVERY_FAILED)
