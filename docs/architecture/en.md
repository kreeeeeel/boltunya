# Architecture

Electron owns the frameless native window, tray and startup registration. It starts a single Kotlin process, waits for the private loopback session descriptor, loads the static Next.js export and removes the handshake secret from browser history. The UI has no Node access; the isolated preload exposes a narrow, sender-checked IPC API.

## Modules

- `desktop/`: backend lifecycle, native window controls, compact Gravity-based tray and cached startup registration.
- `backend/configuration`: Spring Boot and feature integration configurations. A single constructor supplies each component's dependencies; `ObjectProvider` resolves lazy dependencies. `Main` obtains the runtime from the servlet application context.
- `backend/application`: the profile lock, explicit integration startup and coordinated shutdown. Creating the Spring context starts the loopback servlet listener, while Telegram authorization, model warmup and background collection wait for `ApplicationRuntime.start()`.
- `backend/controller`: implementations of the eight generated Spring MVC API groups, plus the static Next.js resource controller. Controllers receive and return generated transport DTOs.
- `backend/component`: feature business operations that call existing application services. `backend/transformer` explicitly maps transport DTOs to and from domain models; there is no generic JSON endpoint adapter.
- `backend/server`: the fixed loopback/ephemeral-port policy, local session, request filter, bounded body preparation and shared exception handling. Session and limit checks use the decoded servlet path used by MVC, including encoded and matrix-parameter URLs. Authorization runs before reading request bodies.
- `backend/validation`: Jakarta validation and application input/model-target rules. Jackson rejects scalar coercion; generated request DTO defaults preserve omitted input fields without weakening required response DTOs.
- `backend/domain`: feature models grouped under `chat`, `media`, `telegram`, `memory`, `ai`, `settings`, `events` and `system`. These remain separate from generated DTOs and retain the existing JSON/Markdown persistence formats.
- `backend/contract`: the canonical split OpenAPI source, generated Spring interfaces/DTOs, generated Kotlin WebClient transport, its closeable typed wrapper and the TypeScript Fetch SDK. Generated output stays under module `build/` directories. See [contract and clients](../api-client/en.md).
- `telegram/`: connection state, cache, versioned update handlers, chat directory, history, media/wallpaper mapping and threaded message sending.
- `ai/`: Spring AI integration, typed conversation context, resource-based model catalog, local model management and personal writing samples; `model/` owns inference and final responses, while `research/` provides web and weather lookup. See [AI services](../ai/en.md).
- `automation/`: shared bounded reply/draft/memory queue, debounce, stale-result cancellation, pacing and Telegram flood-wait handling. Parallelism is configurable; delivery within each conversation remains ordered.

- `monitoring/`: OSHI CPU/RAM/process/network samples and optional NVIDIA GPU readings.
- `web/src/modules/`: workspace, settings, messages, memory and metrics UI. Production requests use generated feature operations through the `@aigram/api-client` workspace alias; shared code also holds themes and RU/EN/KK/UK dictionaries.

Backend package names above are relative to `backend/src/main/kotlin/ru/aigram`. The former private HTTP server/router and `SettingKey` bridge have been removed.

Each Kotlin class has its own file. Prompts, literal language vocabularies, error text and model metadata live in resources. Language dictionaries are loaded using `languages.json`; identifiers use typed rules and standard parsers. Resource templates perform a single substitution pass, so user-supplied variables are not expanded recursively. Concrete exceptions live in `exception/impl` with stable error codes.

## State and delivery

`%LOCALAPPDATA%/Aigram` is the persistent profile; desktop startup safely migrates existing sessions from Tiho. Settings are atomically replaced; credentials use Windows DPAPI. TDLib owns its session database. Existing automated message IDs drive local AI badges and exclusion from personal writing examples.

Launches pause the queue unless assistant startup is explicitly enabled in saved settings. Windows startup opens in the tray. Groups default to disabled and support global/per-chat mention/all overrides. Outgoing messages preserve the configured reply/send mode and forum topic. Local media rendering includes photos, video, audio and stickers. Context contains text/captions; optional image analysis sends the latest photograph or album directly to a vision-capable model. Speech recognition is not provided.

The local API validates the session cookie, Host and Origin. JSON bodies are limited to 512 KiB, including chunked requests; raw upload bodies have a separate 100 MiB limit. Media responses preserve byte ranges and bounded decompression of Telegram stickers. The static export is served from packaged web resources. Technical diagnostics omit conversation text and credentials; full inference traces are stored separately in the local event history.

Full exit closes initiatives, memory collection, media prefetch, the reply queue, servlet listener, app-managed Ollama, monitoring and Telegram. Resource shutdown is idempotent so Spring can also clean up a partially constructed context. External Ollama servers are not terminated. Electron waits for the backend to flush its session before exit.

## Packaging

Gradle validates and bundles the canonical OpenAPI tree before the three generators run. Frontend builds depend on the generated TypeScript SDK; standalone npm commands use `sdk:check` rather than a second schema or type-only transport. The Docker web build performs the same generation in a JDK stage.

The Windows build exports the frontend, bundles it with the Gradle backend distribution, creates a Java runtime using jlink and packages Electron as `complie/Aigram.exe`. The external-consumer SDK is packaged separately as `Aigram-API-Client.tgz`. Ollama and model weights remain optional downloads. Windows CI checks code, builds the executable, runs browser scenarios and publishes a release on main. Docker recipes are development/checking tools; the signed-in Windows desktop session is not containerized. Verification of the current migration is recorded in the [migration checkpoint](../migration/en.md).
