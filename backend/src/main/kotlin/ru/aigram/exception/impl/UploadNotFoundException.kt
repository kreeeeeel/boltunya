package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class UploadNotFoundException : AppException(ErrorCode.UPLOAD_NOT_FOUND)
