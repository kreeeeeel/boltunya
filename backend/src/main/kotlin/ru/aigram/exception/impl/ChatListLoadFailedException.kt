package ru.aigram.exception.impl

import ru.aigram.exception.AppException
import ru.aigram.exception.ErrorCode

class ChatListLoadFailedException : AppException(ErrorCode.CHAT_LIST_LOAD_FAILED)
