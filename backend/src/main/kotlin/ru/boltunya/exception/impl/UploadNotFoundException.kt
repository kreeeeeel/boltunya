package ru.boltunya.exception.impl

import ru.boltunya.exception.AppException
import ru.boltunya.exception.ErrorCode

class UploadNotFoundException : AppException(ErrorCode.UPLOAD_NOT_FOUND)
