Templates adapted from OpenAPI Generator 7.25.0, Copyright 2018 OpenAPI-Generator Contributors.
Upstream: https://github.com/OpenAPITools/openapi-generator/tree/v7.25.0/modules/openapi-generator/src/main/resources
License: Apache License, Version 2.0 (see LICENSE).

Local changes preserve the generated wire API while using Stream API for iteration, braced conditions,
explicit empty-response handling, and direct route literals instead of companion objects holding constants.
The enum template retains the configured Jackson wire factories with explicit error branches and Stream lookup.
The WebClient operation template selects one request content type instead of overwriting it for every declared type.

Optional non-nullable properties with schema defaults retain non-null Kotlin types and constructor defaults.
Empty object defaults instantiate the referenced input model or an empty map; nullable schema properties remain nullable.

The audited `x-kotlin-empty-default` property marker retains canonical empty object defaults when the pinned
Kotlin generator omits them from template metadata. It is restricted to explicitly empty object defaults.
