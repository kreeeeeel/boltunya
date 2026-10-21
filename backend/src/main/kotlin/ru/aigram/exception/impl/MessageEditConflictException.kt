package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MessageEditConflictException : AppException(ErrorCode.MESSAGE_EDIT_CONFLICT)
