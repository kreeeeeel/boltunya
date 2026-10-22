package ru.aigram.configuration

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import org.junit.jupiter.api.Test
import org.springframework.core.io.Resource
import org.springframework.web.bind.annotation.RequestMapping
import ru.aigram.contract.api.MediaApi
import ru.aigram.contract.dto.ModelProfileUpdate
import java.lang.reflect.ParameterizedType
import java.util.Arrays
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeneratedApiBoundaryTest {
    @Test
    fun `binary media signatures preserve raw bytes resource response and arbitrary upload mime types`() {
        val upload = MediaApi::class.java.getMethod("uploadFile", String::class.java, ByteArray::class.java)
        val media = MediaApi::class.java.getMethod("media", Int::class.javaPrimitiveType)

        assertContentEquals(arrayOf("*/*"), upload.getAnnotation(RequestMapping::class.java).consumes)
        assertEquals(ByteArray::class.java, upload.parameterTypes[1])
        assertEquals(Resource::class.java, (media.genericReturnType as ParameterizedType).actualTypeArguments.single())
    }

    @Test
    fun `defaulted objects and maps reject explicit null at constructor parameters`() {
        assertNullFails(ModelProfileUpdate::class.java, "options")
        assertNullFails(ModelProfileUpdate::class.java, "capabilities")
    }

    private fun assertNullFails(type: Class<*>, property: String) {
        val parameter = Arrays.stream(type.declaredConstructors).flatMap { Arrays.stream(it.parameters) }
            .filter { it.getAnnotation(JsonProperty::class.java)?.value == property }.findFirst().orElse(null)

        assertNotNull(parameter)
        assertEquals(Nulls.FAIL, parameter.getAnnotation(JsonSetter::class.java)?.nulls)
    }
}
