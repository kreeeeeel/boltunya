package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ReplyCancelledException : AppException(ErrorCode.REPLY_CANCELLED)
