package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class EngineWarmupFailedException : AppException(ErrorCode.ENGINE_WARMUP_FAILED)
