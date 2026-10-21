package ru.aigram.transformer.chat

import org.springframework.stereotype.Component
import ru.aigram.domain.chat.ContextExclusionScope
import ru.aigram.domain.chat.ContextExclusionUpdate
import ru.aigram.contract.dto.ContextExclusionUpdate as ContractContextExclusionUpdate

@Component
class ContextExclusionTransformer {
    fun toDomain(value: ContractContextExclusionUpdate): ContextExclusionUpdate = ContextExclusionUpdate(
        scope = ContextExclusionScope.valueOf(value.scope.name),
        excluded = value.excluded,
    )
}
