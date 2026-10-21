package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MalformedPayloadException : AppException(ErrorCode.MALFORMED_PAYLOAD)
