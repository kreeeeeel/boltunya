package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ReplyCancelledException : AppException(ErrorCode.REPLY_CANCELLED)
