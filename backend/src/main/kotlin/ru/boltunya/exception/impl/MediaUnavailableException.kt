package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class MediaUnavailableException : InvalidInputException(ErrorCode.MEDIA_UNAVAILABLE)
