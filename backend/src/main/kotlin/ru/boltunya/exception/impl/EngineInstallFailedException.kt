package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class EngineInstallFailedException : AppException(ErrorCode.ENGINE_INSTALL_FAILED)
