package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class MediaTooLargeException : InvalidInputException(ErrorCode.MEDIA_TOO_LARGE)
