package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class EngineRestartUnavailableException : AppException(ErrorCode.ENGINE_RESTART_UNAVAILABLE)
