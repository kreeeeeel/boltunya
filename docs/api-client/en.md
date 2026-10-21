# API contract and clients

[Русская версия](ru.md)

The canonical contract is [`backend/contract/api/schema/api.yaml`](../../backend/contract/api/schema/api.yaml). It describes 60 paths, 74 operations and 114 named schemas. Operation documents live in `schema/operations/`; reusable models and enums live in `schema/models/`. Relative references connect these files.

Spring Boot owns dependency injection and the embedded servlet server. Feature controllers implement all eight generated Spring MVC interfaces; components reuse application services and explicit transformers map generated DTOs to feature domain models. The previous `ru.aigram.http` server/router has been removed. The web application calls the generated feature operations from `@aigram/api-client`; its workspace alias resolves the canonical generated SDK source. The previous `openapi-fetch` transport, separate type generator and schema bridge have been removed.

## Modules

| Gradle project                        | Responsibility                                                                                             | Output                                                           |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `:backend:contract:api`               | Validate and bundle the contract; generate `kotlin-spring` interfaces and DTOs for Spring Boot 4/Jackson 3 | `build/bundled/api.yaml`, `build/generated/openapi`, JVM library |
| `:backend:contract:client:openapi`    | Generate the Kotlin `jvm-spring-webclient` transport, models and eight API groups                          | `build/generated/openapi`, JVM library                           |
| `:backend:contract:client`            | Handwritten `AigramClientFactory` and closeable `AigramClient` wrapper                                     | JVM library exporting the generated client dependency            |
| `:backend:contract:client:typescript` | Generate the Fetch SDK, compile JavaScript and declarations, package and verify a consumer installation    | `build/generated/openapi`, `build/dist`, `build/package`         |

All three generators use OpenAPI Generator **7.25.0**. JVM modules use JDK **21** and Kotlin **2.3.21**. The TypeScript module requires Node.js **22 or newer** and pins TypeScript **5.9.3** in its own lockfile.

The eight groups are `activity`, `chats`, `media`, `memory`, `models`, `settings`, `system` and `telegram`. IDs declared as strings remain strings, including Telegram IDs larger than JavaScript's safe integer range. Named enums retain their wire values. The TypeScript error model is named `ApiError` to avoid a language-name collision; this does not change the JSON error shape.

Six dedicated input schemas preserve the existing constructor defaults for chat rules, model profile options/capabilities, model prompts, appearance and event-history settings. Responses keep their complete, required fields. Kotlin callers now pass the corresponding `*Input` model for those requests. Existing operation names, URLs and JSON field names remain stable; PATCH still distinguishes an absent field from an invalid explicit `null`. Schema defaults, including empty nested objects, are generated rather than filled through a JSON bridge. The reviewed `x-kotlin-empty-default` marker works around missing empty-object metadata in the pinned generator and is validated against the canonical default.

`POST /api/engine/restart` is the additional operation. It restarts only an Ollama process owned by Aigram; an external service returns a typed conflict instead of being stopped. Binary uploads accept arbitrary content types, including WebP, while server responses continue to stream resources and byte ranges.

## Validation and generation

Run these commands from the repository root in PowerShell; use `./gradlew` on other platforms.

```powershell
.\gradlew.bat :backend:contract:api:validateSource
.\gradlew.bat :backend:contract:api:bundleContract
.\gradlew.bat :backend:contract:api:openApiGenerate :backend:contract:client:openapi:openApiGenerate :backend:contract:client:typescript:generateClient
```

`bundleContract` depends on source validation. It produces one generated document with local component references. This preserves named nested types when feeding the external-reference tree to OpenAPI Generator 7.25.0. Each generator validates and consumes that same bundle; the original schema tree is also a Gradle input, so changing a referenced file invalidates generation.

Compile and verify the contract libraries and installable SDK:

```powershell
.\gradlew.bat :backend:contract:api:check :backend:contract:client:check :backend:contract:client:typescript:check
```

The TypeScript chain is `validateContract` → `generateClient` → `npmCi` → `typecheckClient` → `compileClient` → `packageClient` → `consumerSmoke`. Generation cleans its previous output. `npmCi` installs the locked compiler with lifecycle scripts disabled. The smoke test installs the resulting tarball into an isolated temporary npm project in offline mode, compiles real typed imports and exercises generated requests with a mock Fetch implementation.

Change the schema, generator configuration or checked-in templates when fixing generated code. Files under `build/` are disposable outputs.

### Web workspace

Production UI calls use the generated feature clients through the `@aigram/api-client` workspace alias, which resolves `backend/contract/client/typescript/build/generated/openapi/src/index.ts`. The build compiles these sources into the web application; the distributed tarball remains available for external consumers.

Gradle's `buildWeb` depends on `:backend:contract:client:typescript:generateClient`, and `scripts/build.ps1` generates it before npm checks. For standalone web development, first run the canonical generator from the repository root:

```powershell
.\gradlew.bat :backend:contract:client:typescript:generateClient --no-daemon
npm.cmd run check --prefix web
```

The web `sdk:check` preflight reports a missing generated SDK before development, build, type checking or unit tests. It does not generate a separate schema or type-only client. The Docker web build runs the same Gradle generator in a JDK 21 stage before the Node.js frontend build.

## TypeScript package

To build only the distributable:

```powershell
.\gradlew.bat :backend:contract:client:typescript:packageClient -PappVersion=2.6.30
```

The package is named `@aigram/api-client`. Its version follows the root `appVersion` property, whose development default is `2.6.0`. The source `package.json` and lockfile retain that development version; release builds set the generated package version without modifying either source file.

- `backend/contract/client/typescript/build/dist/` contains ESM JavaScript, `.d.ts` declarations, package metadata and template license notices.
- `backend/contract/client/typescript/build/package/manifest.json` records npm's actual filename, version, integrity and file list.
- The same directory contains `aigram-api-client-<version>.tgz`.
- The desktop packaging script copies it to `complie/Aigram-API-Client.tgz`; the release workflow includes this archive and its SHA-256 checksum alongside the application. It does not publish to an npm registry.

Install the archive you built or downloaded:

```shell
npm install ./Aigram-API-Client.tgz
```

### Browser

An explicitly empty `basePath` targets the current origin. The existing API session cookie is sent according to Fetch's credentials policy:

```typescript
import { ChatsApi, Configuration } from "@aigram/api-client";

const chats = new ChatsApi(
    new Configuration({
        basePath: "",
        credentials: "same-origin",
    }),
);

const messages = await chats.history({
    chatId: "-1009007199254740993555",
    before: "9007199254740993999",
});
```

Omitting `basePath` raises `RequiredError` before Fetch runs. The SDK does not infer an address from `window`. Its runtime accepts an injected `fetchApi`, headers and middleware. Responses pass through without runtime model conversion, preserving unknown properties, nullable fields and string IDs; the generated types still describe the declared API.

### Node.js and session rotation

The contract's session scheme uses the `tiho` cookie. Browser code relies on the cookie jar and `credentials`; Node.js callers supply the cookie explicitly. The generator's `apiKey` option does not implement cookie authentication for this scheme.

Resolve a rotating token for each request through middleware:

```typescript
import { Configuration, SystemApi } from "@aigram/api-client";

export function createApi(basePath: string, sessionToken: () => string) {
    return new SystemApi(
        new Configuration({
            basePath,
            middleware: [
                {
                    pre: async ({ url, init }) => {
                        const headers = new Headers(init.headers);
                        headers.set("Cookie", `tiho=${sessionToken()}`);
                        return { url, init: { ...init, headers } };
                    },
                },
            ],
        }),
    );
}
```

Session acquisition belongs to the application or integrating caller. Keep tokens out of URLs and logs. HTTP failures expose `ResponseError.response`; transport failures expose `FetchError.cause`. The SDK adds no retry policy. The consumer smoke verifies that failed requests are made once.

## Kotlin client

Within this Gradle build, depend on `project(":backend:contract:client")`. The public factory is [`AigramClientFactory.create(URI, () -> String)`](../../backend/contract/client/src/main/kotlin/ru/aigram/client/configuration/AigramClientFactory.kt). It requires an HTTP or HTTPS origin without user information, an extra path, query parameters or a fragment.

The factory is an explicit library entry point. Consumers can register it in their own DI container; the module does not automatically create an application client bean. Binary media operations use `ByteArray` for raw response bytes.

The factory bounds buffered responses at **16 MiB**, including binary media. This supports ordinary photos beyond WebClient's 256 KiB default without creating an unlimited buffer. For larger media, supply your own configured `WebClient` to a generated API group with an appropriate bounded codec limit; streaming large downloads is outside this wrapper. The application's existing media viewer uses its separate transport.

`AigramClient` exposes `activity`, `chats`, `media`, `memory`, `models`, `settings`, `system` and `telegram`. Their generated operations return Reactor `Mono`; the wrapper owns its JDK HTTP transport and must be closed. A blocking CLI example is:

```kotlin
import ru.aigram.client.configuration.AigramClientFactory
import ru.aigram.client.generated.model.Message
import java.net.URI

fun history(origin: URI, token: () -> String): List<Message>? =
    AigramClientFactory().create(origin, token).use { client ->
        client.chats.history(
            chatId = "-1009007199254740993555",
            before = "9007199254740993999",
        ).block()
    }
```

Reactive callers can compose the returned `Mono` and close the client after their work completes. The session callback is evaluated on each subscription/request, so token rotation does not require constructing another client. Invalid session values fail before dispatch. The factory disables redirects and adds no automatic retries. HTTP errors follow WebClient's `WebClientResponseException` behavior. A missing body for a required response raises `IllegalStateException` rather than producing a fabricated empty result.

### Jackson compatibility

The generated server contract targets Spring Boot 4/Jackson 3. The Kotlin WebClient generator currently uses its supported `useSpringBoot3` template branch with Jackson 2 serializers on Spring Framework 7. This is a template compatibility setting; it does not downgrade the application to Spring Boot 3.

The wrapper explicitly installs `Jackson2JsonEncoder` and `Jackson2JsonDecoder` with the generated SDK mapper. Server DTOs and client models belong to separate packages. Keep these codec settings when changing transport configuration: relying on Boot 4's default Jackson 3 codec for Jackson 2-generated client models can change serialization behavior.

## Maintenance references

Generator options are documented for [`kotlin-spring`](https://openapi-generator.tech/docs/generators/kotlin-spring/), [`kotlin`](https://openapi-generator.tech/docs/generators/kotlin/) and [`typescript-fetch`](https://openapi-generator.tech/docs/generators/typescript-fetch/). Spring documents WebClient's explicit codec and filter configuration in its [client builder reference](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-builder.html).

Checked-in template adaptations and upstream licenses are recorded in the [server template notice](../../backend/contract/api/templates/NOTICE.md), [Kotlin client template notice](../../backend/contract/client/openapi/templates/NOTICE.md) and [Fetch runtime notice](../../backend/contract/client/typescript/templates/NOTICE.md).
