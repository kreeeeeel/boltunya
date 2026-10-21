package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class UploadLimitReachedException : AppException(ErrorCode.UPLOAD_LIMIT_REACHED)
