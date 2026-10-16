package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class MalformedPayloadException : AppException(ErrorCode.MALFORMED_PAYLOAD)
