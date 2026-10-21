package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class EngineInstallFailedException : AppException(ErrorCode.ENGINE_INSTALL_FAILED)
