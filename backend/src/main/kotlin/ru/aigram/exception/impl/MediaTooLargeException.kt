package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class MediaTooLargeException : InvalidInputException(ErrorCode.MEDIA_TOO_LARGE)
