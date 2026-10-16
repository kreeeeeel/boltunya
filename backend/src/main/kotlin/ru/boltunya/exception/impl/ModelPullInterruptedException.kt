package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class ModelPullInterruptedException : AppException(ErrorCode.MODEL_PULL_INTERRUPTED)
