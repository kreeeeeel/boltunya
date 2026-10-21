package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class InvalidModelPromptException : AppException(ErrorCode.INVALID_MODEL_PROMPT)
