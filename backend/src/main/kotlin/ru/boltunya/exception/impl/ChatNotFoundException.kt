package ru.boltunya.exception.impl

import ru.boltunya.exception.ErrorCode
import ru.boltunya.exception.InvalidInputException

class ChatNotFoundException : InvalidInputException(ErrorCode.CHAT_NOT_FOUND)
