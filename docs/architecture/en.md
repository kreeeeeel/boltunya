# Architecture

Electron owns the frameless native window, tray and startup registration. It starts a single Kotlin process, waits for the private loopback session descriptor, loads the static Next.js export and removes the handshake secret from browser history. The UI has no Node access; the isolated preload exposes a narrow, sender-checked IPC API.

## Modules

- `desktop/`: backend lifecycle, native window controls, compact Gravity-based tray and cached startup registration.
- `backend/`: Kotlin under `ru.aigram`, Spring Boot constructor injection and singleton components. `ApplicationRuntime` starts and closes services and owns the profile lock.
- `http/` (temporary until the [contract migration](../migration/en.md)): session checks, routing, input validation, resources and feature-specific endpoints. `SettingKey` validates PATCH value types.
- `telegram/`: connection state, cache, versioned update handlers, chat directory, history, media/wallpaper mapping and threaded message sending.
- `ai/`: Spring AI integration, typed conversation context, resource-based model catalog, local model management and personal writing samples; `model/` owns inference and final responses, while `research/` provides web and weather lookup. See [AI services](../ai/en.md).
- `automation/`: sequential reply queue, debounce, stale-result cancellation, pacing and Telegram flood-wait handling.

- `monitoring/`: OSHI CPU/RAM/process/network samples and optional NVIDIA GPU readings.
- `web/src/modules/`: workspace, settings, messages, memory and metrics UI. Shared code includes the generated API client, themes and RU/EN/KK/UK dictionaries.

Each Kotlin class has its own file. Prompts, literal language vocabularies, error text and model metadata live in resources. Language dictionaries are loaded using `languages.json`; identifiers use typed rules and standard parsers. Resource templates perform a single substitution pass, so user-supplied variables are not expanded recursively. Concrete exceptions live in `exception/impl` with stable error codes.

## State and delivery

`%LOCALAPPDATA%/Aigram` is the persistent profile; desktop startup safely migrates existing sessions from Tiho. Settings are atomically replaced; credentials use Windows DPAPI. TDLib owns its session database. Existing automated message IDs drive local AI badges and exclusion from personal writing examples.

Launches pause the queue unless assistant startup is explicitly enabled in saved settings. Windows startup opens in the tray. Groups default to disabled and support global/per-chat mention/all overrides. Outgoing messages preserve the configured reply/send mode and forum topic. Local media rendering includes photos, video, audio and stickers. Context contains text/captions; optional image analysis sends the latest photograph or album directly to a vision-capable model. Speech recognition is not provided.

Full exit closes the reply queue, HTTP bridge, app-managed Ollama, monitoring and Telegram. External Ollama servers are not terminated. Electron waits for the backend to flush its session before exit.

## Packaging

The Windows build exports the frontend, bundles it with the Gradle backend distribution, creates a Java runtime using jlink and packages Electron as `complie/Aigram.exe`. Ollama and model weights remain optional downloads. Windows CI checks code, builds the executable, runs browser scenarios and publishes a release on main. Docker recipes are development/checking tools; the signed-in Windows desktop session is not containerized.
