package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class EngineDownloadFailedException(status: Int) :
    AppException(ErrorCode.ENGINE_DOWNLOAD_FAILED, mapOf("status" to status))
