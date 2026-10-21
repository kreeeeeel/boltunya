package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ModelPullInterruptedException : AppException(ErrorCode.MODEL_PULL_INTERRUPTED)
