package ru.aigram.configuration

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.type.LogicalType

@Configuration(proxyBeanMethods = false)
class ApiJsonConfiguration {
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun strictApiJson(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { builder ->
        builder.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        builder.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        builder.withCoercionConfig(LogicalType.Textual) { configuration ->
            configuration.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
        }
        builder.withCoercionConfig(LogicalType.Boolean) { configuration ->
            configuration.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
        }
        builder.withCoercionConfig(LogicalType.Integer) { configuration ->
            configuration.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
        }
        builder.withCoercionConfig(LogicalType.Float) { configuration ->
            configuration.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
            configuration.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
        }
    }
}
