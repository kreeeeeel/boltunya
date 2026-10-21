package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ActivityNotFoundException : AppException(ErrorCode.ACTIVITY_NOT_FOUND)
