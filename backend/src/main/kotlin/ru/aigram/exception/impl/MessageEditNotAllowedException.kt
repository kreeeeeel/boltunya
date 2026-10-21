package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MessageEditNotAllowedException : AppException(ErrorCode.MESSAGE_EDIT_NOT_ALLOWED)
