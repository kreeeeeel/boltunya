Templates adapted from OpenAPI Generator 7.25.0, Copyright 2018 OpenAPI-Generator Contributors.
Upstream: https://github.com/OpenAPITools/openapi-generator/tree/v7.25.0/modules/openapi-generator/src/main/resources
License: Apache License, Version 2.0 (see LICENSE).

Local changes preserve the generated wire API while using Stream API for iteration, braced conditions,
explicit empty-response handling, and direct route literals instead of companion objects holding constants.

Optional non-nullable property null rejection is emitted on constructor parameters as well as fields.
Raw upload operations with `x-spring-consumes-any` retain their canonical wildcard MIME support;
the upstream Spring generator otherwise drops `*/*` when explicit binary MIME alternatives exist.

Optional non-nullable properties with schema defaults retain non-null Kotlin types and constructor defaults.
Empty object defaults instantiate the referenced input model or an empty map; nullable schema properties remain nullable.

The audited `x-kotlin-empty-default` property marker retains canonical empty object defaults when the pinned
Kotlin generator omits them from template metadata. It is restricted to explicitly empty object defaults.
