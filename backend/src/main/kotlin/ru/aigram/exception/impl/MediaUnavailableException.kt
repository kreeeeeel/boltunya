package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class MediaUnavailableException : InvalidInputException(ErrorCode.MEDIA_UNAVAILABLE)
