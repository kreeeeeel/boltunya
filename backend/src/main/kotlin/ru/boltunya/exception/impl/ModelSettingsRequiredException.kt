package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class ModelSettingsRequiredException : InvalidInputException(ErrorCode.MODEL_SETTINGS_REQUIRED)
