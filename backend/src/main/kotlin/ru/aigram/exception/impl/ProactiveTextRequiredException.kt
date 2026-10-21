package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class ProactiveTextRequiredException : InvalidInputException(ErrorCode.PROACTIVE_TEXT_REQUIRED)
