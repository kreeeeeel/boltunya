package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelProfileChangedException : AppException(ErrorCode.MODEL_PROFILE_CHANGED)
