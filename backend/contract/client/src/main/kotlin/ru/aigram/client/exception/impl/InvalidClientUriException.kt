package ru.aigram.client.exception.impl

import ru.aigram.client.exception.AigramClientErrorCode
import ru.aigram.client.exception.AigramClientException

class InvalidClientUriException : AigramClientException(AigramClientErrorCode.INVALID_URI)
