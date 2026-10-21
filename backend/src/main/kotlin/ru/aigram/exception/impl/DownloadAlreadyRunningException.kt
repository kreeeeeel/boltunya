package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class DownloadAlreadyRunningException : InvalidInputException(ErrorCode.DOWNLOAD_ALREADY_RUNNING)
