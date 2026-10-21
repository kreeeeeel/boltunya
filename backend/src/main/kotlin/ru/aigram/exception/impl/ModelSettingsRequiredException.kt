package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class ModelSettingsRequiredException : InvalidInputException(ErrorCode.MODEL_SETTINGS_REQUIRED)
