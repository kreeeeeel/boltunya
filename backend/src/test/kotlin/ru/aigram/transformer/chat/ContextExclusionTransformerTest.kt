package ru.aigram.transformer.chat

import org.junit.jupiter.api.Test
import ru.aigram.domain.chat.ContextExclusionScope
import ru.aigram.domain.chat.ContextExclusionUpdate
import kotlin.test.assertEquals
import ru.aigram.contract.dto.ContextExclusionScope as ContractContextExclusionScope
import ru.aigram.contract.dto.ContextExclusionUpdate as ContractContextExclusionUpdate

class ContextExclusionTransformerTest {
    private val transformer = ContextExclusionTransformer()

    @Test
    fun `empty legacy update excludes only the selected message`() {
        assertEquals(ContextExclusionUpdate(), transformer.toDomain(ContractContextExclusionUpdate()))
    }

    @Test
    fun `retains range restoration rather than substituting exclusion defaults`() {
        assertEquals(
            ContextExclusionUpdate(ContextExclusionScope.THROUGH, false),
            transformer.toDomain(ContractContextExclusionUpdate(ContractContextExclusionScope.THROUGH, false)),
        )
    }
}
