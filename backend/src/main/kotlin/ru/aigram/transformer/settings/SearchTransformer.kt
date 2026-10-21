package ru.aigram.transformer.settings

import ru.aigram.contract.dto.SearchConnection
import ru.aigram.contract.dto.SearchConnectionState
import ru.aigram.domain.ai.research.SearchConnection as DomainSearchConnection

fun DomainSearchConnection.toContract(): SearchConnection = SearchConnection(
    configured,
    SearchConnectionState.valueOf(state.name),
)
