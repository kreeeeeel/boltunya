package ru.aigram.resources

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ResourceCatalogTest {
    @Test
    fun `renders resources without evaluating replacement values as templates`() {
        val resources = ResourceCatalog()
        val text = resources.render(
            "testing/resource-template.txt",
            mapOf(
                "value0" to "{{secret}}",
                "secret" to "hidden",
            ),
        )
        assertEquals("на {{secret}}", text)
    }

    @Test
    fun `preserves malformed and unknown placeholders and handles adjacent and nested opening braces`() {
        val resources = ResourceCatalog()
        assertEquals(
            "hello/hello {{unknown}} {{invalid key}} {{unfinished",
            resources.interpolate("{{key}}/{{key}} {{unknown}} {{invalid key}} {{unfinished", mapOf("key" to "hello")),
        )
        assertEquals("{{hello", resources.interpolate("{{{{key}}", mapOf("key" to "hello")))
        assertEquals("", resources.interpolate("{{missing}}", emptyMap(), preserveUnknown = false))
        assertEquals("value", resources.interpolate("{{data.last}}", mapOf("data.last" to "value")))
    }
}
