package ru.aigram.transformer.settings

import org.springframework.stereotype.Component
import ru.aigram.contract.dto.SearchConnection
import ru.aigram.contract.dto.SearchConnectionState
import ru.aigram.domain.ai.research.SearchConnection as DomainSearchConnection

@Component
class SearchTransformer {
    fun toContract(value: DomainSearchConnection): SearchConnection = SearchConnection(
        value.configured,
        SearchConnectionState.valueOf(value.state.name),
    )
}
