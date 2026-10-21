package ru.aigram.transformer.chat

import ru.aigram.domain.chat.ContextExclusionScope
import ru.aigram.domain.chat.ContextExclusionUpdate
import ru.aigram.contract.dto.ContextExclusionUpdate as ContractContextExclusionUpdate

fun ContractContextExclusionUpdate.toDomain(): ContextExclusionUpdate = ContextExclusionUpdate(
    scope = ContextExclusionScope.valueOf(scope.name),
    excluded = excluded,
)
