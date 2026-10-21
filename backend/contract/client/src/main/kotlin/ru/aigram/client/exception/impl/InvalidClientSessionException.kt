package ru.aigram.client.exception.impl

import ru.aigram.client.exception.AigramClientErrorCode
import ru.aigram.client.exception.AigramClientException

class InvalidClientSessionException : AigramClientException(AigramClientErrorCode.INVALID_SESSION)
