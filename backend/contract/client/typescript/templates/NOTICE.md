`runtime.mustache` is adapted from OpenAPI Generator 7.25.0, maintained by OpenAPITools:
https://github.com/OpenAPITools/openapi-generator/blob/v7.25.0/modules/openapi-generator/src/main/resources/typescript-fetch/runtime.mustache

The upstream project is licensed under Apache License 2.0 (see `LICENSE.txt`).
The sole behavioral customization requires an explicit `Configuration.basePath` before a request;
an explicitly supplied empty string supports browser requests to the current origin.
All other transport behavior remains the upstream Fetch runtime.
