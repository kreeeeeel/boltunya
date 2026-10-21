package ru.aigram.exception.impl

import ru.aigram.exception.ErrorCode
import ru.aigram.exception.InvalidInputException

class ChatNotFoundException : InvalidInputException(ErrorCode.CHAT_NOT_FOUND)
