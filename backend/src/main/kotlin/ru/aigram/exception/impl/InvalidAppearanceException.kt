package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class InvalidAppearanceException : AppException(ErrorCode.INVALID_APPEARANCE)
