package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class MessageDeletionNotAllowedException : AppException(ErrorCode.MESSAGE_DELETION_NOT_ALLOWED)
