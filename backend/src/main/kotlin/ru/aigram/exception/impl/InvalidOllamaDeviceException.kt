package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class InvalidOllamaDeviceException : AppException(ErrorCode.INVALID_OLLAMA_DEVICE)
