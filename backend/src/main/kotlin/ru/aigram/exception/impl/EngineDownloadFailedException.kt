package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class EngineDownloadFailedException(status: Int) :
    AppException(ErrorCode.ENGINE_DOWNLOAD_FAILED, mapOf("status" to status))
