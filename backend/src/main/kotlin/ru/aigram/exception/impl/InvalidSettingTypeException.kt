package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class InvalidSettingTypeException : InvalidInputException(ErrorCode.INVALID_SETTING_TYPE)
